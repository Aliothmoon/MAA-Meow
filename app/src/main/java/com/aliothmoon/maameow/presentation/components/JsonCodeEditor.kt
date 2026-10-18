package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aliothmoon.maameow.R
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.subscribeAlways
import org.eclipse.tm4e.core.registry.IThemeSource

private var textMateInitialized = false

private fun ensureTextMateInitialized(context: android.content.Context, isDark: Boolean) {
    val themeRegistry = ThemeRegistry.getInstance()

    if (!textMateInitialized) {
        textMateInitialized = true
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))

        FileProviderRegistry.getInstance()
            .tryGetInputStream("textmate/quietlight.json")?.let { stream: java.io.InputStream ->
                themeRegistry.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(stream, "textmate/quietlight.json", null),
                        "quietlight"
                    ).apply { this.isDark = false }
                )
            }

        FileProviderRegistry.getInstance()
            .tryGetInputStream("textmate/darcula.json")?.let { stream: java.io.InputStream ->
                themeRegistry.loadTheme(
                    ThemeModel(
                        IThemeSource.fromInputStream(stream, "textmate/darcula.json", null),
                        "darcula"
                    ).apply { this.isDark = true }
                )
            }

        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    themeRegistry.setTheme(if (isDark) "darcula" else "quietlight")
}

/** Shared Sora editor UI. Each screen owns its files, validation policy and save behavior. */
@Composable
fun JsonCodeEditor(
    loadedText: String?,
    isJsonValid: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val onChange by rememberUpdatedState(onTextChange)
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var editVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(editorRef, loadedText) {
        val editor = editorRef ?: return@LaunchedEffect
        val text = loadedText ?: return@LaunchedEffect
        if (editor.text.toString() != text) editor.setText(text)
    }
    // 配色只随深浅色切换重建，不跟着每次按键走
    LaunchedEffect(editorRef, isDark) {
        val editor = editorRef ?: return@LaunchedEffect
        ensureTextMateInitialized(editor.context, isDark)
        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    }

    Column(modifier = modifier) {
        JsonStatusBar(isValid = isJsonValid)
        AndroidView(
            factory = { ctx ->
                ensureTextMateInitialized(ctx, isDark)
                CodeEditor(ctx).apply {
                    typefaceText = android.graphics.Typeface.MONOSPACE
                    setTextSize(13f) // sora-editor 内部按 sp 处理，不使用 applyDimension
                    setPinLineNumber(true)
                    colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    setEditorLanguage(TextMateLanguage.create("source.json", true))
                    subscribeAlways<ContentChangeEvent> {
                        onChange(text.toString())
                        editVersion++
                    }
                }
            },
            update = { editor ->
                editorRef = editor
                editor.isEditable = enabled
            },
            onRelease = { it.release() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        HorizontalDivider()
        SymbolInputBar(editor = editorRef, editVersion = editVersion, enabled = enabled)
    }
}

@Composable
private fun SymbolInputBar(editor: CodeEditor?, editVersion: Int, enabled: Boolean) {
    // 显示文本 → (实际插入文本, 光标偏移)
    // 成对符号：offset=1 使光标落在括号内；关键字：offset=自身长度使光标落在末尾
    val symbols = listOf(
        "{}" to Pair("{}", 1),
        "[]" to Pair("[]", 1),
        "\"\"" to Pair("\"\"", 1),
        ":" to Pair(":", 1),
        "," to Pair(",", 1),
        "." to Pair(".", 1),
        "true" to Pair("true", 4),
        "false" to Pair("false", 5),
        "null" to Pair("null", 4),
    )

    val canUndo by remember(editVersion) { derivedStateOf { editor?.canUndo() == true } }
    val canRedo by remember(editVersion) { derivedStateOf { editor?.canRedo() == true } }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(onClick = { editor?.undo() }, enabled = enabled && canUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.editor_action_undo),
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            IconButton(onClick = { editor?.redo() }, enabled = enabled && canRedo) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.editor_action_redo),
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 4.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            IconButton(onClick = { editor?.moveSelection(SelectionMovement.UP) }) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.editor_move_up),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { editor?.moveSelection(SelectionMovement.DOWN) }) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.editor_move_down),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { editor?.moveSelection(SelectionMovement.LEFT) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.editor_move_left),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { editor?.moveSelection(SelectionMovement.RIGHT) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.editor_move_right),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 4.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            symbols.forEach { (display, pair) ->
                val (insert, offset) = pair
                TextButton(
                    enabled = enabled,
                    onClick = { editor?.insertText(insert, offset) },
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text = display,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonStatusBar(isValid: Boolean) {
    val (bg, fg, label) = if (isValid) {
        Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            stringResource(R.string.editor_json_valid)
        )
    } else {
        Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(R.string.editor_json_invalid)
        )
    }
    Surface(color = bg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}
