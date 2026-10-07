package com.eatmoreduck.ruleengine.dsl

/**
 * 校验问题严重级别。
 *
 * - ERROR：结构非法，执行引擎不应接受（如空逻辑组、悬空边）；
 * - WARNING：结构可疑但运行时有兜底行为（如空规则集走通过分支）。
 */
enum class IssueSeverity {
    ERROR,
    WARNING,
}

/**
 * 一条校验问题。
 *
 * @param severity 严重级别
 * @param path 问题所在位置，JSONPath 风格（如 `$.rules[0].condition.children[1]`、`$.nodes[id=end-1]`）
 * @param message 问题描述（中文，指明具体字段与原因）
 */
data class ValidationIssue(
    val severity: IssueSeverity,
    val path: String,
    val message: String,
)

/**
 * 校验结果：问题清单 + 便捷判定。
 */
data class ValidationResult(
    val issues: List<ValidationIssue>,
) {
    /** 无 ERROR 级问题即视为结构合法（WARNING 不阻断） */
    val isValid: Boolean
        get() = issues.none { it.severity == IssueSeverity.ERROR }

    val errors: List<ValidationIssue>
        get() = issues.filter { it.severity == IssueSeverity.ERROR }

    val warnings: List<ValidationIssue>
        get() = issues.filter { it.severity == IssueSeverity.WARNING }
}

/**
 * 规则 DSL 结构校验器。
 *
 * 校验口径与前端编辑器约束及旧后端执行语义对齐：
 * - 前端 `dslGenerator.isEmptyCondition` 对应「无条件叶子」检查；
 * - 前端编辑器允许递归嵌套，此处以上限防御过深嵌套；
 * - 旧后端对缺分支/缺节点均为运行时兜底，此处前移为 WARNING/ERROR。
 */
class DslValidator(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    /** 条件树默认嵌套深度上限（组套组层数） */
    companion object {
        const val DEFAULT_MAX_DEPTH: Int = 20
    }

    // ============ 规则定义树 ============

    /**
     * 校验规则定义树。
     *
     * 规则：
     * - 空逻辑组（children 为空）→ ERROR；
     * - 原子条件缺 fieldName → ERROR（对应前端 isEmptyCondition）；
     * - 字符串阈值为空白 → ERROR；
     * - 组套组嵌套深度超过 [maxDepth] → ERROR；
     * - 无任何规则 → WARNING（运行时恒返回默认动作）。
     */
    fun validate(definition: RuleDefinition): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        if (definition.rules.isEmpty()) {
            issues += warning("$.rules", "规则未配置任何条件规则，执行时恒返回默认动作")
        }
        definition.rules.forEachIndexed { index, group ->
            val rootPath = "$.rules[$index].condition"
            validateConditionTree(group.condition, rootPath, groupDepth = 0, issues = issues)
        }
        return ValidationResult(issues)
    }

    /**
     * 校验单规则配置（复用规则定义树的节点规则）。
     */
    fun validate(config: SingleRuleConfig): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        validateConditionTree(config.condition, "$.condition", groupDepth = 0, issues = issues)
        return ValidationResult(issues)
    }

    private fun validateConditionTree(
        node: ConditionTreeNode,
        path: String,
        groupDepth: Int,
        issues: MutableList<ValidationIssue>,
    ) {
        when (node) {
            is ConditionNode -> {
                if (node.fieldName.isBlank()) {
                    issues += error(path, "原子条件缺少字段名 fieldName")
                }
                val threshold = node.threshold
                if (threshold is ThresholdValue.Text && threshold.value.isBlank()) {
                    issues += error(path, "原子条件缺少阈值 threshold")
                }
            }

            is LogicGroup -> {
                if (node.children.isEmpty()) {
                    issues += error(path, "空逻辑组：logic=${node.logic} 的分组不包含任何子节点")
                    return
                }
                // 嵌套深度只统计逻辑组层数（根组为第 1 层）
                val depth = groupDepth + 1
                if (depth > maxDepth) {
                    issues += error(path, "逻辑组嵌套深度 $depth 超过上限 $maxDepth")
                    return
                }
                node.children.forEachIndexed { index, child ->
                    validateConditionTree(child, "$path.children[$index]", depth, issues)
                }
            }
        }
    }

    // ============ 决策流图 ============

    /**
     * 校验决策流图。
     *
     * 规则：
     * - 必须恰好一个 start 节点 → 否则 ERROR；
     * - 缺少 end 节点 → ERROR（结束节点是必备终局，所有路径收口到它）；
     * - 外层 `type` 与 `data.nodeType` 不一致 → ERROR（前后端读取口径不同，不一致会导致行为分裂）；
     * - 边引用不存在的节点 → ERROR；
     * - start 节点必须有出边，end 节点不应有出边；
     * - 连接语义 → ERROR：入边仅结束/合并节点允许多条；出边仅条件节点允许两条
     *   （是/否各一），其余一条；重复平行线拒绝；
     * - 存在环 → ERROR（必须是 DAG；执行期另有步数上限兜底，保存期前置拦截）；
     * - 条件节点出边未覆盖 conditionMet=true/false → WARNING；
     * - 条件节点缺 fieldName/阈值 → ERROR；
     * - 规则集节点 ruleKeys 为空 → WARNING（运行时走通过分支）；
     * - 名单节点缺 keyType → ERROR；
     * - 存在从 start 不可达的节点 → WARNING。
     */
    fun validate(graph: FlowGraph): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val nodesById = graph.nodes.associateBy { it.id }

        // start/end 节点数量
        val starts = graph.nodes.filter { it.data is StartNodeData }
        if (starts.isEmpty()) issues += error("$", "流程图缺少开始节点（nodeType=start）")
        if (starts.size > 1) issues += error("$", "流程图存在 ${starts.size} 个开始节点，仅允许一个")
        if (graph.nodes.none { it.data is EndNodeData }) {
            issues += error("$", "流程图缺少结束节点（nodeType=end），所有路径必须收口到结束节点")
        }

        // 节点级检查（路径以节点 id 定位，与 JSON 内容直接对应）
        graph.nodes.forEach { node ->
            val nodePath = "$.nodes[id=${node.id}]"
            if (node.id.isBlank()) issues += error(nodePath, "节点缺少 id")
            node.type?.let { outer ->
                if (outer != node.data.nodeType.jsonValue) {
                    issues +=
                        error(
                            nodePath,
                            "节点 type='$outer' 与 data.nodeType='${node.data.nodeType.jsonValue}' 不一致",
                        )
                }
            }
            when (val data = node.data) {
                is ConditionNodeData -> {
                    val branches = data.effectiveBranches
                    if (branches.isEmpty()) issues += error("$nodePath.data", "条件节点没有任何条件分支")
                    branches.forEach { branch ->
                        if (branch.fieldName.isBlank()) {
                            issues += error("$nodePath.data", "条件分支 ${branch.id} 缺少字段名 fieldName")
                        }
                        val threshold = branch.threshold
                        if (threshold is ThresholdValue.Text && threshold.value.isBlank()) {
                            issues += error("$nodePath.data", "条件分支 ${branch.id} 缺少阈值 threshold")
                        }
                    }
                    val branchIds = branches.map { it.id }
                    if (branchIds.distinct().size < branchIds.size) {
                        issues += error("$nodePath.data", "条件分支 id 重复: ${branchIds.groupBy { it }.filterValues { it.size > 1 }.keys}")
                    }
                }

                is RuleSetNodeData -> {
                    if (data.ruleKeys.isEmpty()) {
                        issues += warning("$nodePath.data", "规则集节点未引用任何规则，执行时走通过分支")
                    }
                }

                is BlacklistNodeData -> {
                    if (data.keyType.isBlank()) issues += error("$nodePath.data", "黑名单节点缺少名单键类型 keyType")
                }

                is WhitelistNodeData -> {
                    if (data.keyType.isBlank()) issues += error("$nodePath.data", "白名单节点缺少名单键类型 keyType")
                }

                else -> {
                    Unit
                }
            }
        }

        // 边级检查
        val outEdgesBySource = graph.edges.groupBy { it.source }
        graph.edges.forEachIndexed { index, edge ->
            val edgePath = "$.edges[$index]"
            if (edge.source !in nodesById) issues += error(edgePath, "边 ${edge.id} 的 source='${edge.source}' 不存在")
            if (edge.target !in nodesById) issues += error(edgePath, "边 ${edge.id} 的 target='${edge.target}' 不存在")
        }

        // 连接语义：入度/出度约束（结束/合并节点多入是本职；条件节点每分支+兜底各限一条出边）
        graph.nodes.forEach { node ->
            val outs = outEdgesBySource[node.id].orEmpty()
            val ins = graph.edges.count { it.target == node.id }
            when (val data = node.data) {
                is ConditionNodeData -> {
                    if (ins > 1) issues += error("$.nodes[id=${node.id}]", "条件节点只允许一条入边（当前 $ins 条）")
                    val maxOuts = data.effectiveBranches.size + 1
                    if (outs.size > maxOuts) {
                        issues +=
                            error(
                                "$.nodes[id=${node.id}]",
                                "条件节点最多 $maxOuts 条出边（每分支一条 + 兜底，当前 ${outs.size} 条）",
                            )
                    }
                    val handles = outs.map { it.sourceHandle }
                    if (handles.distinct().size < handles.size) {
                        issues += error("$.nodes[id=${node.id}]", "条件节点的同一分支只允许一条出边")
                    }
                }

                is EndNodeData, is MergeNodeData -> {
                    Unit
                }

                is StartNodeData -> {
                    Unit
                }

                else -> {
                    if (ins > 1) issues += error("$.nodes[id=${node.id}]", "节点只允许一条入边（当前 $ins 条）")
                    if (outs.size > 1) issues += error("$.nodes[id=${node.id}]", "节点只允许一条出边（当前 ${outs.size} 条）")
                }
            }
        }

        // 重复平行线：同 source + sourceHandle + target 只允许一条
        val seenConnections = HashSet<Triple<String, String?, String>>()
        graph.edges.forEachIndexed { index, edge ->
            val key = Triple(edge.source, edge.sourceHandle, edge.target)
            if (!seenConnections.add(key)) {
                issues += error("$.edges[$index]", "重复连线：${edge.source} → ${edge.target} 已存在相同连线")
            }
        }

        // start/end 与出边
        starts.forEach { start ->
            if (outEdgesBySource[start.id].isNullOrEmpty()) {
                issues += error("$.nodes[id=${start.id}]", "开始节点没有出边")
            }
        }
        graph.nodes
            .filter { it.data is EndNodeData }
            .filter { !outEdgesBySource[it.id].isNullOrEmpty() }
            .forEach { issues += warning("$.nodes[id=${it.id}]", "结束节点不应有出边") }

        // 断头路：除开始（缺出边已是 ERROR）与结束外的节点没有出边，路径无法收口到结束节点
        graph.nodes
            .filter { it.data !is StartNodeData && it.data !is EndNodeData }
            .filter { outEdgesBySource[it.id].isNullOrEmpty() }
            .forEach { issues += warning("$.nodes[id=${it.id}]", "节点没有出边，流程在此中断，建议连接到结束节点") }

        // 条件节点分支覆盖：每个条件分支与兜底出口都建议有出边（缺失执行时中断）
        graph.nodes
            .filter { it.data is ConditionNodeData }
            .forEach { node ->
                val conditionData = node.data as ConditionNodeData
                val handles = outEdgesBySource[node.id].orEmpty().map { it.sourceHandle }
                val missing =
                    branchesHandleIds(conditionData).filter { it !in handles }
                if (missing.isNotEmpty()) {
                    issues +=
                        warning(
                            "$.nodes[id=${node.id}]",
                            "条件节点的以下分支缺少出边（执行时将中断）: ${missing.joinToString(", ")}",
                        )
                }
            }

        // DAG 检测：三色 DFS 找回边（back edge），命中即存在环（含自环），
        // 且直接给出从回边目标到当前节点的精确环路径，便于定位
        val outTargets = HashMap<String, MutableList<String>>()
        graph.edges.forEach { edge ->
            if (edge.source in nodesById && edge.target in nodesById) {
                outTargets.getOrPut(edge.source) { mutableListOf() }.add(edge.target)
            }
        }
        val color = HashMap<String, Int>()
        graph.nodes.forEach { color[it.id] = 0 } // 0 未访问 / 1 在递归栈 / 2 已完成
        val stack = ArrayDeque<String>()

        fun findCycle(id: String): List<String>? {
            color[id] = 1
            stack.addLast(id)
            for (next in outTargets[id].orEmpty()) {
                when (color[next]) {
                    1 -> {
                        val path = stack.toList()
                        return path.subList(path.indexOf(next), path.size) + next
                    }

                    0 -> {
                        findCycle(next)?.let { return it }
                    }
                }
            }
            stack.removeLast()
            color[id] = 2
            return null
        }

        val cycle = graph.nodes.firstNotNullOfOrNull { node -> if (color.getValue(node.id) == 0) findCycle(node.id) else null }
        if (cycle != null) {
            issues += error("$", "流程图存在环，必须是有向无环图（DAG）: ${cycle.joinToString(" → ")}")
        }

        // 可达性：从 start 沿边遍历，标记可达节点
        starts.firstOrNull()?.let { start ->
            val reachable = mutableSetOf(start.id)
            val queue = ArrayDeque(listOf(start.id))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                outEdgesBySource[current]
                    .orEmpty()
                    .map { it.target }
                    .filter { it in nodesById && it !in reachable }
                    .forEach {
                        reachable += it
                        queue += it
                    }
            }
            graph.nodes
                .filter { it.id !in reachable }
                .forEach { issues += warning("$.nodes[id=${it.id}]", "节点从开始节点不可达") }
        }

        return ValidationResult(issues)
    }

    /** 条件节点全部出口 handle：各分支 id + 兜底 */
    private fun branchesHandleIds(data: ConditionNodeData): List<String> = data.effectiveBranches.map { it.id } + data.elseHandle

    private fun error(
        path: String,
        message: String,
    ): ValidationIssue = ValidationIssue(IssueSeverity.ERROR, path, message)

    private fun warning(
        path: String,
        message: String,
    ): ValidationIssue = ValidationIssue(IssueSeverity.WARNING, path, message)
}
