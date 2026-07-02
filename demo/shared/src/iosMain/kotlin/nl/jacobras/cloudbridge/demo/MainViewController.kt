package nl.jacobras.cloudbridge.demo

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import nl.jacobras.cloudbridge.demo.ui.DemoScreen
import nl.jacobras.cloudbridge.demo.ui.DemoViewModel

fun MainViewController() = ComposeUIViewController {
    val viewModel = remember { DemoViewModel() }

    DemoScreen(
        viewModel = viewModel,
        onAuthenticate = { service ->
            TODO(service)
        }
    )
}