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

package com.wdstechnology.android.kryten.sax;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class WapProvisioningDocContentHandler implements SimpleContentHandler {

    private static final String ROOT_DOCUMENT_ELEMENT = "ROOT";
    private static final String CHARACTERISTIC_DOCUMENT_ELEMENT = "characteristic";
    private static final String PARM_DOCUMENT_ELEMENT = "parm";
    private static final String APPLICATION_ID_PATH = "[ROOT, wap-provisioningdoc, characteristic[type=APPLICATION], parm[name=APPID]]";
    private static final String MNC_PATH = "[ROOT, wap-provisioningdoc, characteristic[type=VENDORCONFIG], parm[name=MNC]]";
    private static final String MCC_PATH = "[ROOT, wap-provisioningdoc, characteristic[type=VENDORCONFIG], parm[name=MCC]]";
    private static final String VENDORCONFIG_ID_PATH = "[ROOT, wap-provisioningdoc, characteristic[type=VENDORCONFIG], parm[name=NAME]]";
    private static final String BOOTSTRAP_NAME_PATH = "[ROOT, wap-provisioningdoc, characteristic[type=BOOTSTRAP], parm[name=NAME]]";
    private static final String NAPDEF_BEARER_TYPE_PATH = "[ROOT, wap-provisioningdoc, characteristic[type=NAPDEF], parm[name=BEARER]]";
    private static final String NAPDEF_ADDRTYPE_PATH = "[ROOT, wap-provisioningdoc, characteristic[type=NAPDEF], parm[name=NAP-ADDRTYPE]]";
    private static final String NAPDEF_ADDRESS_PATH = "[ROOT, wap-provisioningdoc, characteristic[type=NAPDEF], parm[name=NAP-ADDRESS]]";
    private static final String NAPDEF_AUTHNAME = "[ROOT, wap-provisioningdoc, characteristic[type=NAPDEF], characteristic[type=NAPAUTHINFO], parm[name=AUTHNAME]]";
    private static final String NAPDEF_AUTHSECRET = "[ROOT, wap-provisioningdoc, characteristic[type=NAPDEF], characteristic[type=NAPAUTHINFO], parm[name=AUTHSECRET]]";

    public Map<String, String> mParameters = new HashMap<String, String>();
    private Stack<String> mContext = new Stack<String>();
    private boolean mHasParsedCompleteDocument = false;

    @Override
    public void startDocument() {
        mContext.push(ROOT_DOCUMENT_ELEMENT);
    }

    @Override
    public void startElement(String elementName, Map<String, String> attributes) {
        mContext.push(createContextPath(elementName, attributes));

        if (PARM_DOCUMENT_ELEMENT.equals(elementName)) {
            addParameter(attributes);
        }
    }

    @Override
    public void endElement(String elementName) {
        mContext.pop();
    }

    @Override
    public void endDocument() {
        mHasParsedCompleteDocument = ROOT_DOCUMENT_ELEMENT.equals(mContext.pop())
                && mContext.empty();
    }

    public boolean isDocumentWellFormed() {
        return mHasParsedCompleteDocument;
    }

    public Map<String, String> getParameters() {

        HashMap<String, String> p = new HashMap<String, String>();
        p.put("name", mParameters.get(BOOTSTRAP_NAME_PATH));
        p.put("apn", mParameters.get(NAPDEF_ADDRESS_PATH));
        p.put("user", mParameters.get(NAPDEF_AUTHNAME));
        p.put("password", mParameters.get(NAPDEF_AUTHSECRET));
        p.put("mcc", mParameters.get(MCC_PATH));
        p.put("mnc", mParameters.get(MNC_PATH));

        return p;
    }

    private String createContextPath(String elementName, Map<String, String> attributes) {
        if (CHARACTERISTIC_DOCUMENT_ELEMENT.equals(elementName)) {
            return elementName + "[type=" + attributes.get("type") + "]";
        } else if (PARM_DOCUMENT_ELEMENT.equals(elementName)) {
            return elementName + "[name=" + attributes.get("name") + "]";
        } else {
            return elementName;
        }
    }

    private void addParameter(Map<String, String> attributes) {
        mParameters.put(mContext.toString(), attributes.get("value"));
    }

    public boolean isDocumentValid() {
        String name = mParameters.get(BOOTSTRAP_NAME_PATH);
        String bearer = mParameters.get(NAPDEF_BEARER_TYPE_PATH);
        String napType = mParameters.get(NAPDEF_ADDRTYPE_PATH);
        String vendorId = mParameters.get(VENDORCONFIG_ID_PATH);
        String mcc = mParameters.get(MCC_PATH);
        String mnc = mParameters.get(MNC_PATH);
        String applicationId = mParameters.get(APPLICATION_ID_PATH);

        return name != null && !name.equals("") && "GSM-GPRS".equals(bearer)
                && "APN".equals(napType) && "NETWORKIDENT".equals(vendorId) && mcc != null
                && !mcc.equals("") && mnc != null && !mnc.equals("") && "w2".equals(applicationId);
    }

    public String getName() {
        return mParameters.get(BOOTSTRAP_NAME_PATH);
    }

}
