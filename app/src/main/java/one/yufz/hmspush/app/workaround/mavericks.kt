package one.yufz.hmspush.app.workaround

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel

@Composable
inline fun <reified VM : MavericksViewModel<S>, reified S : MavericksState> mavericksViewModel(
    noinline keyFactory: (() -> String)? = null,
    noinline argsFactory: (() -> Any?)? = null,
): VM {
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "ViewModelStoreOwner not found. Did you forget to add rememberViewModelStoreNavEntryDecorator()?"
    }
    val savedStateRegistryOwner = checkNotNull(LocalSavedStateRegistryOwner.current) {
        "SavedStateRegistryOwner not found. Did you forget to add rememberSavedStateNavEntryDecorator()?"
    }

    val mavericksScope = remember(lifecycleOwner, viewModelStoreOwner, savedStateRegistryOwner) {
        object : ViewModelStoreOwner by viewModelStoreOwner,
            SavedStateRegistryOwner by savedStateRegistryOwner {
            override val lifecycle: Lifecycle
                get() = lifecycleOwner.lifecycle
        }
    }

    return com.airbnb.mvrx.compose.mavericksViewModel(
        scope = mavericksScope,
        keyFactory = keyFactory,
        argsFactory = argsFactory,
    )
}