import com.googlecode.aviator.AviatorEvaluator
import com.googlecode.aviator.Options

fun main() {
    val e1 = AviatorEvaluator.getInstance()
    e1.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true)
    e1.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.COMPILE)
    val expr1 = e1.compile("amount * 2 + 100", true)
    println("cached+COMPILE variables: " + expr1.variableNames)
    val expr2 = e1.compile("amount * 2 + 100", false)
    println("uncached+COMPILE variables: " + expr2.variableNames)
    val e2 = AviatorEvaluator.newInstance()
    val expr3 = e2.compile("amount * 2 + 100", false)
    println("default optimize variables: " + expr3.variableNames)
    println("result: " + expr1.execute(mapOf("amount" to 400)))
}
