package dev.jacobandersen.bastion.post

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    type = ApplicationModule.Type.CLOSED,
    allowedDependencies = ["url", "util"],
)
class ModuleMetadata
