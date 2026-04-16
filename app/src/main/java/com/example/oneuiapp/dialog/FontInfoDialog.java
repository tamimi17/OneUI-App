package com.example.oneuiapp.dialog;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.Map;

import com.example.oneuiapp.R;

public class FontInfoDialog {

    private final Context context;
    private final Map<String, String> metadata;
    private final String originalFileName;
    private final String originalPath;

    public FontInfoDialog(Context context, Map<String, String> metadata, String originalFileName, String originalPath) {
        this.context = context;
        this.metadata = metadata;
        this.originalFileName = originalFileName;
        this.originalPath = originalPath;
    }

    // للتوافق مع الكود القديم
    public FontInfoDialog(Context context, Map<String, String> metadata) {
        this(context, metadata, null, null);
    }

    public void show() {
        if (metadata == null || metadata.isEmpty()) {
            showNoMetadataDialog();
            return;
        }

        int secondaryColor = context.getResources().getColor(R.color.secondary_text_color, context.getTheme());
        String secondaryColorHex = String.format("#%06X", (0xFFFFFF & secondaryColor));

        StringBuilder htmlBuilder = new StringBuilder();

        String[] orderedKeys = {
                "Copyright",
                "FullName",
                "PostScriptName",
                "Family",
                "SubFamily",
                "Weight",
                "Width",
                "FontType",
                "VariableInstances",
                "Version",
                "ModifiedDate",
                "CreatedDate",
                "Designer",
                "DesignerURL",
                "Manufacturer",
                "VendorURL",
                "VendorID",
                "Trademark",
                "Description",
                "LicenseDescription",
                "LicenseURL",
                "SupportedScripts",
                "GlyphCount",
                "UnitsPerEm",
                "FileName",
                "Path"
        };

        String[] displayNames = {
                "Copyright",
                "Full Name",
                "PostScript Name",
                "Family",
                "SubFamily",
                "Weight",
                "Width",
                "Font Type",
                "Variable Instances",
                "Version",
                "Modified Date",
                "Created Date",
                "Designer",
                "Designer URL",
                "Manufacturer",
                "Vendor URL",
                "Vendor ID",
                "Trademark",
                "Description",
                "License Description",
                "License URL",
                "Supported Scripts",
                "Glyph Count",
                "Units Per Em",
                "File Name",
                "Path"
        };

        boolean hasContent = false;

        for (int i = 0; i < orderedKeys.length; i++) {
            String key = orderedKeys[i];
            String displayName = displayNames[i];
            String value = metadata.get(key);
            if ("FileName".equals(key) && originalFileName != null) {
            value = originalFileName;
            } else if ("Path".equals(key) && originalPath != null) {
            value = originalPath;
        }

            if (value != null && !value.isEmpty()) {
                if ("Version".equals(key)) {
                    value = cleanVersionString(value);
                }

                if (hasContent) {
                    htmlBuilder.append("<br><br>");
                }

                htmlBuilder.append("<font color='").append(secondaryColorHex).append("'>")
                        .append(displayName)
                        .append("</font><br>");

                if (isUrl(value)) {
                    htmlBuilder.append("<a href='").append(value).append("'>")
                            .append(android.text.TextUtils.htmlEncode(value))
                            .append("</a>");
                } else {
                    String escapedValue = android.text.TextUtils.htmlEncode(value);
                    htmlBuilder.append(escapedValue);
                }

                hasContent = true;
            }
        }

        if (!hasContent) {
            htmlBuilder.append("No metadata available.");
        }

        Spanned formattedText;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            formattedText = Html.fromHtml(htmlBuilder.toString(), Html.FROM_HTML_MODE_LEGACY);
        } else {
            formattedText = Html.fromHtml(htmlBuilder.toString());
        }

        String dialogTitle = metadata.containsKey("FullName") && metadata.get("FullName") != null 
                && !metadata.get("FullName").isEmpty() ?
                metadata.get("FullName") : 
                (metadata.containsKey("Family") && metadata.get("Family") != null 
                && !metadata.get("Family").isEmpty() ?
                metadata.get("Family") : context.getString(R.string.font_viewer_select_font));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(dialogTitle)
                .setMessage(formattedText)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextSize(16);
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            Linkify.addLinks(messageView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            messageView.setLinkTextColor(context.getResources().getColor(R.color.blue_text_color, context.getTheme()));
        }
    }

    private void showNoMetadataDialog() {
        new AlertDialog.Builder(context)
                .setTitle("Font Information")
                .setMessage("No metadata available.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private boolean isUrl(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lowerText = text.toLowerCase().trim();
        return lowerText.startsWith("http://") || lowerText.startsWith("https://") || lowerText.startsWith("www.");
    }

    private String cleanVersionString(String version) {
        if (version == null || version.isEmpty()) {
            return version;
        }

        String lowerVersion = version.toLowerCase().trim();
        if (lowerVersion.startsWith("version ")) {
            return version.substring(8).trim();
        }

        return version;
    }
          }
