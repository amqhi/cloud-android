/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.Context
import android.widget.Toast

fun showExportFailure(context: Context) {
    Toast.makeText(context, R.string.file_viewer_export_failure, Toast.LENGTH_SHORT).show()
}