package dev.jacobandersen.bastion.indieauth

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    type = ApplicationModule.Type.CLOSED,
    allowedDependencies = ["url", "config::config-security"],
)
class ModuleMetadata
