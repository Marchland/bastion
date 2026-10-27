package dev.jacobandersen.bastion.api

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    type = ApplicationModule.Type.CLOSED,
    allowedDependencies = [
        "post::post-read",
        "webmention::webmention-read",
    ],
)
class ModuleMetadata
