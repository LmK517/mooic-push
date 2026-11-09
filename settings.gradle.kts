@file:Suppress("UnstableApiUsage")

// 阿里云镜像对境外 CI runner 会返回 502，导致整个 plugin 解析中断。
// 本地（国内）仍然优先用镜像，CI 上直接走官方源。
// 注意：pluginManagement 在脚本主体之前求值，这里只能内联判断，不能引用脚本里的 val。

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        if (System.getenv("CI").isNullOrEmpty()) {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        if (System.getenv("CI").isNullOrEmpty()) {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        mavenCentral()
    }
}

rootProject.name = "Mooic"
include(":app")
include(":ncmapi")
