package dev.jacobandersen.bastion.websub

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    type = ApplicationModule.Type.CLOSED,
    allowedDependencies = ["post::post-events", "util"],
)
class ModuleMetadata
