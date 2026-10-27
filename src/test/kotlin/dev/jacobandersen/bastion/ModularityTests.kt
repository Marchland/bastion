package dev.jacobandersen.bastion

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTests {
    val modules = ApplicationModules.of(BastionApplication::class.java)

    @Test
    fun verifyModules() {
        modules.verify()
    }

    @Test
    fun writeDocs() {
        Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml()
    }
}
