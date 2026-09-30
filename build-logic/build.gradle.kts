plugins {
    `kotlin-dsl`
}

dependencies {
    // 约定插件需要应用到子项目的基础插件，必须出现在本 included build 的 classpath 上
    implementation(libs.kotlin.gradlePlugin)
    // kotlin("plugin.spring") 对应的编译器插件（为 @Service 等注解打开 final 类）
    implementation(libs.kotlin.allopen)
    implementation(libs.springBoot.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
}
