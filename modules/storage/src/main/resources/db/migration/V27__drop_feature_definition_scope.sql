-- V27: 移除特征定义的 scope（适用范围）字段
-- 该字段全链路无运行时消费（决策/规则执行零引用），确认为历史遗留元数据
ALTER TABLE feature_definition DROP COLUMN scope;
