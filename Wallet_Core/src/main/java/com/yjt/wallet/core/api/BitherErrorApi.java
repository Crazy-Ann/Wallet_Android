/*
 * Copyright 2014 http://Bither.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yjt.wallet.core.api;

import com.yjt.wallet.core.api.http.BitherUrl;
import com.yjt.wallet.core.api.http.HttpPostResponse;
import com.yjt.wallet.core.utils.Utils;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.util.ArrayList;
import java.util.List;

public class BitherErrorApi extends HttpPostResponse<String> {

    private static String ERROR_MSG = "error_msg";
    private String mErrorMsg;

    public BitherErrorApi(String errorMsg) {
        this.mErrorMsg = errorMsg;
        setUrl(BitherUrl.BITHER_ERROR_API);
    }

    @Override
    public HttpEntity getHttpEntity() throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        if (!Utils.isEmpty(this.mErrorMsg)) {
            params.add(new BasicNameValuePair(ERROR_MSG, this.mErrorMsg.trim()));
        }
        return new UrlEncodedFormEntity(params, HTTP.UTF_8);
    }

    @Override
    public void setResult(String response) throws Exception {

    }

}
