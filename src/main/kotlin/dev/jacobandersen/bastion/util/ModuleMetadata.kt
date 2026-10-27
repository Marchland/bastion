package dev.jacobandersen.bastion.util

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    type = ApplicationModule.Type.CLOSED,
    allowedDependencies = [],
)
class ModuleMetadata
