package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PlaylistEntity
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmOrange

/**
 * Диалог добавления нового IPTV-плейлиста по URL в формате M3U (и необязательной ссылки XMLTV).
 * Поддерживает как сенсорный ввод, так и навигацию с пульта ТВ.
 */
@Composable
fun AddPlaylistDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, epgUrl: String?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var epgUrl by rememberSaveable { mutableStateOf("") }
    val urlFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { urlFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.PlaylistAdd,
                contentDescription = null,
                tint = WarmOrange,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.dialog_add_playlist_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !isLoading,
                    singleLine = true,
                    label = { Text(stringResource(R.string.dialog_playlist_name_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_playlist_name_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    enabled = !isLoading,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = WarmOrange
                        )
                    },
                    label = { Text(stringResource(R.string.dialog_playlist_url_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_playlist_url_hint)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(urlFocusRequester)
                        .testTag("playlist_url_input")
                )

                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    enabled = !isLoading,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = WarmOrange
                        )
                    },
                    label = { Text(stringResource(R.string.dialog_epg_url_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_epg_url_hint)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isLoading) onConfirm(name, url, epgUrl.ifBlank { null })
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_epg_url_input")
                )

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            color = WarmOrange,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.playlists_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, url, epgUrl.ifBlank { null }) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmOrange,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier.testTag("confirm_add_playlist_button")
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading,
                modifier = Modifier.testTag("cancel_add_playlist_button")
            ) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * Диалог настроек плейлиста: изменение названия и ссылки на телегид XMLTV (.xml или .xml.gz).
 */
@Composable
fun PlaylistSettingsDialog(
    playlist: PlaylistEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, epgUrl: String?, loadEpgNow: Boolean) -> Unit
) {
    var name by rememberSaveable(playlist.id) { mutableStateOf(playlist.name) }
    var epgUrl by rememberSaveable(playlist.id) { mutableStateOf(playlist.epgUrl.orEmpty()) }
    val epgFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { epgFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = WarmOrange,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.dialog_playlist_settings_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.dialog_playlist_name_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_playlist_name_input")
                )

                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = WarmOrange
                        )
                    },
                    label = { Text(stringResource(R.string.dialog_epg_url_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_epg_url_hint)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onSave(name, epgUrl.ifBlank { null }, epgUrl.isNotBlank())
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(epgFocusRequester)
                        .testTag("settings_epg_url_input")
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (epgUrl.isNotBlank()) {
                    Button(
                        onClick = { onSave(name, epgUrl.trim(), true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmOrange,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier.testTag("save_and_refresh_epg_button")
                    ) {
                        Text(stringResource(R.string.dialog_save_and_load_epg))
                    }
                } else {
                    Button(
                        onClick = { onSave(name, null, false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmOrange,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier.testTag("save_settings_button")
                    ) {
                        Text(stringResource(R.string.dialog_save_settings))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_settings_button")
            ) {
                Text(stringResource(R.string.dialog_cancel), color = TextSecondary)
            }
        }
    )
}

/**
 * Диалог подтверждения удаления плейлиста.
 */
@Composable
fun DeletePlaylistDialog(
    playlist: PlaylistEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_delete_message, playlist.name),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed,
                    contentColor = TextPrimary
                ),
                modifier = Modifier.testTag("confirm_delete_playlist_button")
            ) {
                Text(stringResource(R.string.dialog_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_playlist_button")
            ) {
                Text(stringResource(R.string.dialog_cancel), color = TextSecondary)
            }
        }
    )
}
