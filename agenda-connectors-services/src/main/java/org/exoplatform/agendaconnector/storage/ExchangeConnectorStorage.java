/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agendaconnector.storage;

import org.exoplatform.agenda.storage.AgendaRemoteEventStorage;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;

public class ExchangeConnectorStorage {

  private SettingService      settingService;

  private AgendaRemoteEventStorage remoteEventStorage;

  public ExchangeConnectorStorage(SettingService settingService, AgendaRemoteEventStorage remoteEventStorage) {
    this.settingService = settingService;
    this.remoteEventStorage = remoteEventStorage;
  }

  public void createExchangeSetting(ExchangeUserSetting exchangeUserSetting, long userIdentityId) {

    String encodedPassword = ExchangeConnectorUtils.encode(exchangeUserSetting.getPassword());

    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            ExchangeConnectorUtils.EXCHANGE_USERNAME_KEY,
                            SettingValue.create(exchangeUserSetting.getUsername()));
    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            ExchangeConnectorUtils.EXCHANGE_PASSWORD_KEY,
                            SettingValue.create(encodedPassword));
    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            ExchangeConnectorUtils.EXCHANGE_CREDENTIAL_CHECKED,SettingValue.create(exchangeUserSetting.isCredentialChecked()));
  }

  public ExchangeUserSetting getExchangeSetting(long userIdentityId) {

    SettingValue<?> username = this.settingService.get(Context.USER.id(String.valueOf(userIdentityId)),
            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
            ExchangeConnectorUtils.EXCHANGE_USERNAME_KEY);
    SettingValue<?> password = this.settingService.get(Context.USER.id(String.valueOf(userIdentityId)),
            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
            ExchangeConnectorUtils.EXCHANGE_PASSWORD_KEY);
    SettingValue<?> credentialChecked = this.settingService.get(Context.USER.id(String.valueOf(userIdentityId)),
                                                       ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                                                       ExchangeConnectorUtils.EXCHANGE_CREDENTIAL_CHECKED);


    ExchangeUserSetting exchangeUserSetting = new ExchangeUserSetting();
    if (username != null) {
      exchangeUserSetting.setUsername((String) username.getValue());
    }
    if (username != null) {
      String decodePassword = ExchangeConnectorUtils.decode((String) password.getValue());
      exchangeUserSetting.setPassword(decodePassword);
    }
    if(credentialChecked!=null) {
      exchangeUserSetting.setCredentialChecked((boolean) credentialChecked.getValue());
    } else {
      exchangeUserSetting.setCredentialChecked(false);
    }
    return exchangeUserSetting;
  }
  
  public void deleteExchangeSetting(long userIdentityId) {

    this.settingService.remove(Context.USER.id(String.valueOf(userIdentityId)),
            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
            ExchangeConnectorUtils.EXCHANGE_USERNAME_KEY);
    this.settingService.remove(Context.USER.id(String.valueOf(userIdentityId)),
                               ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                               ExchangeConnectorUtils.EXCHANGE_PASSWORD_KEY);
    this.settingService.remove(Context.USER.id(String.valueOf(userIdentityId)),
                               ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                               ExchangeConnectorUtils.EXCHANGE_CREDENTIAL_CHECKED);
  }

  public  void deleteRemoteEvent(long eventId, long userIdentityId){
    remoteEventStorage.deleteRemoteEvent(eventId,userIdentityId);
  }

}
