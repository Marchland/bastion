package dev.jacobandersen.bastion.webmention

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    type = ApplicationModule.Type.CLOSED,
    allowedDependencies = [
        "post::post-events", "post::post-query", "post::post-url",
        "url", "util",
    ],
)
class ModuleMetadata
