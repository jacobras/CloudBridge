package nl.jacobras.cloudbridge.demo.ui

import nl.jacobras.cloudbridge.CloudService
import org.jetbrains.compose.resources.DrawableResource

internal data class ServiceWithInfo(
    val service: CloudService,
    val logo: DrawableResource,
    val name: String,
    val userName: String = "",
    val emailAddress: String = ""
)