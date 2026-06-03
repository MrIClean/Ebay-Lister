package com.example.ebaylister.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ebaylister.ui.EbayPolicyOption
import com.example.ebaylister.ui.ListingEditorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailsScreen(
    editor: ListingEditorState,
    latestCapturePath: String?,
    fulfillmentPolicies: List<EbayPolicyOption>,
    returnPolicies: List<EbayPolicyOption>,
    accountOptionsStatus: String,
    isLoadingAccountOptions: Boolean,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onConditionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onShippingProfileChange: (String) -> Unit,
    onShippingPolicySelected: (String) -> Unit,
    onReturnPolicyChange: (String) -> Unit,
    onReturnPolicySelected: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onRefreshAccountOptions: () -> Unit,
    onAddPhotoPath: (String) -> Unit,
    onRemovePhotoPath: (String) -> Unit,
    onPublishToEbay: () -> Unit,
    onViewPublishedListing: (String) -> Unit,
    onSharePublishedListing: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { onAddPhotoPath(it.toString()) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xAA0B1220),
                        Color(0xEE070B14),
                    ),
                ),
            ),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onClose, label = { Text("Back to drafts") })
                AssistChip(onClick = { photoPicker.launch("image/*") }, label = { Text("Add photos") })
                AssistChip(
                    onClick = onRefreshAccountOptions,
                    enabled = !isLoadingAccountOptions,
                    label = { Text(if (isLoadingAccountOptions) "Loading eBay" else "Refresh eBay") },
                )
                if (!latestCapturePath.isNullOrBlank()) {
                    AssistChip(onClick = { onAddPhotoPath(latestCapturePath) }, label = { Text("Use latest capture") })
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Listing details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = editor.title,
                        onValueChange = onTitleChange,
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )

                    OutlinedTextField(
                        value = editor.price,
                        onValueChange = onPriceChange,
                        label = { Text("Price") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = editor.description,
                        onValueChange = onDescriptionChange,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ListingDropdown(
                            label = "Condition",
                            value = editor.condition,
                            options = listOf(
                                EbayPolicyOption("new", "New"),
                                EbayPolicyOption("open-box", "Open box"),
                                EbayPolicyOption("pre-owned", "Pre-owned"),
                                EbayPolicyOption("parts", "For parts or not working"),
                            ),
                            onSelect = { onConditionChange(it.name) },
                            modifier = Modifier.weight(1f),
                        )

                        OutlinedTextField(
                            value = editor.quantity.toString(),
                            onValueChange = onQuantityChange,
                            label = { Text("Qty") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }

                    OutlinedTextField(
                        value = editor.category,
                        onValueChange = onCategoryChange,
                        label = { Text("eBay category") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    ListingDropdown(
                        label = "Shipping policy",
                        value = editor.shippingProfile,
                        options = fulfillmentPolicies,
                        onSelect = { onShippingPolicySelected(it.id) },
                        fallbackValueChange = onShippingProfileChange,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ListingDropdown(
                        label = "Return policy",
                        value = editor.returnPolicy,
                        options = returnPolicies,
                        onSelect = { onReturnPolicySelected(it.id) },
                        fallbackValueChange = onReturnPolicyChange,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = accountOptionsStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = "Photos (${editor.photoPaths.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        items(editor.photoPaths, key = { it }) { path ->
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ListingPhotoPreview(path = path)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { onRemovePhotoPath(path) }, label = { Text("Remove") })
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Publish", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(editor.publishStatus, style = MaterialTheme.typography.bodyMedium)

                    if (editor.validationErrors.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            editor.validationErrors.forEach { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onPublishToEbay,
                        enabled = !editor.isPublishing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (editor.isPublishing) "Publishing..." else "Publish to eBay")
                    }

                    if (editor.isPublishing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (editor.publishedListingUrl.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { onViewPublishedListing(editor.publishedListingUrl) },
                                label = { Text("View listing") },
                            )
                            AssistChip(
                                onClick = { onSharePublishedListing(editor.publishedListingUrl) },
                                label = { Text("Share listing") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ListingDropdown(
    label: String,
    value: String,
    options: List<EbayPolicyOption>,
    onSelect: (EbayPolicyOption) -> Unit,
    modifier: Modifier = Modifier,
    fallbackValueChange: ((String) -> Unit)? = null,
) {
    var expanded by remember(label, options) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (options.isEmpty() && fallbackValueChange != null) {
            OutlinedTextField(
                value = value,
                onValueChange = fallbackValueChange,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.name)
                                    if (option.description.isNotBlank()) {
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelect(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingPhotoPreview(path: String) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        if (path.startsWith("content://")) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(path)).use { stream ->
                    if (stream != null) BitmapFactory.decodeStream(stream) else null
                }
            }.getOrNull()
        } else {
            BitmapFactory.decodeFile(path)
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Listing photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = false) {},
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Photo preview unavailable", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
