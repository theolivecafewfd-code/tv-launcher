package dev.wiptv.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Field(val label: String, val hint: String) {
    SERVER("Server address", "e.g. http://example.com:8080, or paste your M3U link"),
    USERNAME("Username", "From your provider"),
    PASSWORD("Password", "From your provider"),
}

/**
 * Sign in with the three details an Xtream Codes provider gives you. Each box opens the keyboard
 * on its own screen when you press OK, which is easier with a remote than typing in place.
 */
@Composable
fun LoginScreen(update: UpdateHandle, onSignedIn: (Login) -> Unit) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Field?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstField = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { firstField.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    fun valueOf(field: Field) = when (field) {
        Field.SERVER -> server
        Field.USERNAME -> username
        Field.PASSWORD -> password
    }

    fun signIn() {
        val login = Login(Xtream.normaliseServer(server), username.trim(), password)
        when {
            login.server.isEmpty() -> error = "Enter the server address first."
            login.username.isEmpty() || login.password.isEmpty() -> error = "Enter your username and password."
            else -> scope.launch {
                busy = true
                error = null
                try {
                    Xtream.signIn(login)
                    onSignedIn(login)
                } catch (e: XtreamException) {
                    error = e.message
                } catch (e: Exception) {
                    error = "Sign-in failed: ${e.message ?: e.javaClass.simpleName}"
                }
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        UpdatePill(update, modifier = Modifier.align(Alignment.TopEnd))

        Column(
            modifier = Modifier.align(Alignment.Center).width(460.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Sign in to your provider",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.text),
            )
            Text(
                text = "Use the Xtream Codes details your IPTV provider sent you. They're kept on this TV only, encrypted.",
                style = TextStyle(fontSize = 13.sp, color = Palette.quiet),
            )
            Spacer(Modifier.height(4.dp))

            for (field in Field.entries) {
                FieldButton(
                    field = field,
                    value = valueOf(field),
                    onClick = { editing = field },
                    modifier = if (field == Field.SERVER) Modifier.focusRequester(firstField) else Modifier,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerButton(
                    text = if (busy) "Signing in…" else "Sign in",
                    onClick = { if (!busy) signIn() },
                    primary = true,
                )
            }
            error?.let {
                Text(text = it, style = TextStyle(fontSize = 13.sp, color = Palette.warn))
            }
        }
    }

    editing?.let { field ->
        EditDialog(
            field = field,
            initial = valueOf(field),
            onDone = { text ->
                when (field) {
                    Field.SERVER -> {
                        // A pasted M3U link fills in all three boxes at once.
                        val fromLink = Xtream.fromM3uLink(text)
                        if (fromLink != null) {
                            server = fromLink.server
                            username = fromLink.username
                            password = fromLink.password
                        } else {
                            server = text.trim()
                        }
                    }
                    Field.USERNAME -> username = text
                    Field.PASSWORD -> password = text
                }
                error = null
                editing = null
            },
            onCancel = { editing = null },
        )
    }
}

/** One of the three boxes: shows its label and current value; OK opens the keyboard. */
@Composable
private fun FieldButton(field: Field, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.tile,
            contentColor = Palette.text,
            focusedContainerColor = Palette.tileFocused,
            focusedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, Palette.quiet.copy(alpha = 0.3f)), shape = shape),
            focusedBorder = Border(border = BorderStroke(2.dp, Palette.glow), shape = shape),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text = field.label, style = TextStyle(fontSize = 11.sp, color = Palette.quiet))
            val shown = when {
                value.isEmpty() -> field.hint
                field == Field.PASSWORD -> "•".repeat(value.length.coerceAtMost(16))
                else -> value
            }
            Text(
                text = shown,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = if (value.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                    color = if (value.isEmpty()) Palette.quiet.copy(alpha = 0.7f) else Palette.text,
                ),
            )
        }
    }
}

/** Full-screen typing for one box, with the keyboard up. Done saves; Back cancels. */
@Composable
private fun EditDialog(field: Field, initial: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var reveal by remember { mutableStateOf(false) }
    val input = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { input.requestFocus() }.isSuccess) {
                keyboard?.show()
                return@LaunchedEffect
            }
            delay(50)
        }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.background)
                .padding(top = 40.dp, start = 48.dp, end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = field.label, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.text))
            Box(
                modifier = Modifier
                    .width(560.dp)
                    .background(Palette.tile, RoundedCornerShape(12.dp))
                    .border(2.dp, Palette.glow, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (text.isEmpty()) {
                    Text(text = field.hint, style = TextStyle(fontSize = 16.sp, color = Palette.quiet))
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = Palette.text, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Palette.glow),
                    visualTransformation = if (field == Field.PASSWORD && !reveal) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field) {
                            Field.SERVER -> KeyboardType.Uri
                            Field.PASSWORD -> KeyboardType.Password
                            Field.USERNAME -> KeyboardType.Text
                        },
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        onDone(text)
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(input),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerButton(text = "Done", onClick = { onDone(text) }, primary = true)
                if (field == Field.PASSWORD) {
                    PlayerButton(text = if (reveal) "Hide password" else "Show password", onClick = { reveal = !reveal })
                }
                PlayerButton(text = "Cancel", onClick = onCancel)
            }
        }
    }
}
