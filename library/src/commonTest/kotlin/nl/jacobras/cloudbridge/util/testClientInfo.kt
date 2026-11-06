package nl.jacobras.cloudbridge.util

import nl.jacobras.cloudbridge.auth.ClientInfo

/**
 * [ClientInfo] to create services with in tests.
 */
internal val testClientInfo = ClientInfo(clientId = "testClient")