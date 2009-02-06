/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wdstechnology.android.kryten.persister;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.wdstechnology.android.kryten.sax.WapProvisioningDocContentHandler;

import java.util.Map;

public class ApnPersister {

	private Context mContext;

	public ApnPersister(Context context) {
		mContext = context;
	}

	public void save(WapProvisioningDocContentHandler provisioningDocument) {
	    
	    Map<String, String> parameters = provisioningDocument.getParameters();
	    ContentValues contentValues = new ContentValues();
        
        contentValues.put("name", parameters.get("name"));
        contentValues.put("apn", parameters.get("apn"));
        contentValues.put("user", parameters.get("user"));
        contentValues.put("password", parameters.get("password"));
        String mcc = parameters.get("mcc");
        String mnc = parameters.get("mnc");
        contentValues.put("mcc", mcc);
        contentValues.put("mnc", mnc);
        
        contentValues.put("numeric", mcc + mnc);
	    
		Uri uri = Uri.parse("content://telephony/carriers");
		mContext.getContentResolver().insert(uri, contentValues);
	}

}
