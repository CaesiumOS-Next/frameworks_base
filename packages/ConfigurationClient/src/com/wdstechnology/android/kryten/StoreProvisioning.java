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

package com.wdstechnology.android.kryten;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import com.wdstechnology.android.kryten.persister.ApnPersister;
import com.wdstechnology.android.kryten.sax.WapProvisioningDocContentHandler;
import com.wdstechnology.android.kryten.wbxml.WbxmlSaxParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class StoreProvisioning extends Activity {

    private WapProvisioningDocContentHandler mProvisioningDocument;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.check_provisioning);
        ((Button)findViewById(R.id.accept)).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                ApnPersister persister = new ApnPersister(getApplicationContext());
                persister.save(mProvisioningDocument);
                Toast completeMessage = Toast.makeText(StoreProvisioning.this,
                        R.string.phone_now_configured, Toast.LENGTH_LONG);

                completeMessage.setGravity(Gravity.CENTER, 0, 0);
                completeMessage.show();
                StoreProvisioning.this.finish();
            }
        });
        ((Button)findViewById(R.id.decline)).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                StoreProvisioning.this.finish();
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();

        byte[] wbxmlDocument = getIntent().getByteArrayExtra(
                "com.wdstechnology.android.kryten.provisioning-data");

        mProvisioningDocument = new WapProvisioningDocContentHandler();
        WbxmlSaxParser parser = new WbxmlSaxParser();
        try {
            parser.parse(new ByteArrayInputStream(wbxmlDocument), mProvisioningDocument);

            if (mProvisioningDocument.isDocumentWellFormed()) {

                if (mProvisioningDocument.isDocumentValid()) {
                    ((TextView)findViewById(R.id.check_prov_name_value))
                            .setText(mProvisioningDocument.getName());

                } else {
                    ProvisioningFailed.fail(this, R.string.provisioning_failure);
                    finish();
                    return;
                }
            }

        } catch (XmlPullParserException e) {
            ProvisioningFailed.fail(this, R.string.provisioning_failure);
            finish();
            return;
        } catch (IOException e) {
            ProvisioningFailed.fail(this, R.string.provisioning_failure);
            finish();
            return;
        }
    }

    public static void provision(Context context, byte[] document) {
        Intent storeProvisioningIntent = new Intent(context, StoreProvisioning.class);
        storeProvisioningIntent.putExtra("com.wdstechnology.android.kryten.provisioning-data",
                document);
        context.startActivity(storeProvisioningIntent);
    }

}
