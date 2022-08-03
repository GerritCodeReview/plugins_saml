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

import org.pac4j.saml.profile.SAML2Profile;

public class AuthenticatedUser implements java.io.Serializable {
  private static final long serialVersionUID = 1L;

  private final String username;
  private final String displayName;
  private final String email;
  private final String externalId;
  private final SAML2Profile profile;

  public AuthenticatedUser(String username, String displayName, String email, String externalId, SAML2Profile profile) {
    this.username = username;
    this.displayName = displayName;
    this.email = email;
    this.externalId = externalId;
    this.profile = profile;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getEmail() {
    return email;
  }

  public String getExternalId() {
    return externalId;
  }

  public SAML2Profile getProfile() {
    return profile;
  }

  @Override
  public String toString() {
    return "AuthenticatedUser{"
        + "username='"
        + username
        + '\''
        + ", displayName='"
        + displayName
        + '\''
        + ", email='"
        + email
        + '\''
        + ", externalId='"
        + externalId
        + '\''
        + ", profile='"
        + profile.toString()
        + '\''
        + '}';
  }
}
