package dev.jacobandersen.bastion.micropub

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    type = ApplicationModule.Type.CLOSED,
    allowedDependencies = [
        "post::post-commands", "post::post-query", "post::post-metadata",
        "post::post-syndication", "post::post-types",
        "indieauth::indieauth-token-query", "indieauth::indieauth-scopes",
        "media::media-storage",
        "util", "config::config-security",
    ],
)
class ModuleMetadata
