// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.saml;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class HttpSerlvetBufferedStatusResponse extends HttpServletResponseWrapper {
  private int status;
  private String statusMsg;
  private int error;
  private String errorMsg;

  public HttpSerlvetBufferedStatusResponse(HttpServletResponse response) {
    super(response);
  }

  @Override
  public void sendError(int sc) throws IOException {
    sendError(sc, null);
  }

  @Override
  public void sendError(int sc, String msg) throws IOException {
    error = sc;
    errorMsg = msg;
  }

  @Override
  public void setStatus(int sc) {
    setStatus(sc, null);
  }

  @Override
  public void setStatus(int sc, String msg) {
    status = sc;
    statusMsg = msg;
  }

  public void commit() throws IOException {
    if (error > 0) {
      if (errorMsg != null) {
        super.sendError(error, errorMsg);
      } else {
        super.sendError(error);
      }
    } else if (status > 0) {
      if (statusMsg != null) {
        super.setStatus(status, statusMsg);
      } else {
        super.setStatus(status);
      }
    }
  }
}
