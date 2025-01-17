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
package org.exoplatform.agendaconnector.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.exoplatform.agenda.model.RemoteEvent;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.storage.ExchangeConnectorStorage;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.LogicalOperator;
import microsoft.exchange.webservices.data.core.enumeration.service.ConflictResolutionMode;
import microsoft.exchange.webservices.data.core.enumeration.service.DeleteMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SendInvitationsMode;
import microsoft.exchange.webservices.data.core.enumeration.service.SendInvitationsOrCancellationsMode;
import microsoft.exchange.webservices.data.core.exception.service.local.ServiceLocalException;
import microsoft.exchange.webservices.data.core.service.item.Appointment;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.AppointmentSchema;
import microsoft.exchange.webservices.data.property.complex.FolderId;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.property.definition.PropertyDefinition;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class ExchangeConnectorServiceImpl implements ExchangeConnectorService {

  private ExchangeConnectorStorage exchangeConnectorStorage;

  private AgendaRemoteEventService agendaRemoteEventService;

  private static final Log LOG = ExoLogger.getLogger(ExchangeConnectorServiceImpl.class);


  public ExchangeConnectorServiceImpl(ExchangeConnectorStorage exchangeConnectorStorage,
                                      AgendaRemoteEventService agendaRemoteEventService) {
    this.exchangeConnectorStorage = exchangeConnectorStorage;
    this.agendaRemoteEventService = agendaRemoteEventService;
  }

  @Override
  public void createExchangeSetting(ExchangeUserSetting exchangeUserSetting, long userIdentityId) throws IllegalAccessException {
    try (ExchangeService exchangeService = ExchangeConnectorUtils.connectExchangeServer(exchangeUserSetting)) {
      exchangeConnectorStorage.createExchangeSetting(exchangeUserSetting, userIdentityId);
    } catch (Exception e) {
      LOG.error("Error when user {} tries to connect to exchange server",userIdentityId,e);
      throw new IllegalAccessException("User " + userIdentityId + " is not allowed to connect to exchange server");
    }
  }

  @Override
  public ExchangeUserSetting getExchangeSetting(long userIdentityId) {
    return exchangeConnectorStorage.getExchangeSetting(userIdentityId);
  }

  @Override
  public void deleteExchangeSetting(long userIdentityId) {
    exchangeConnectorStorage.deleteExchangeSetting(userIdentityId);
  }

  @Override
  public List<EventEntity> getExchangeEvents(long userIdentityId,
                                             String start,
                                             String end,
                                             ZoneId userTimeZone) throws IllegalAccessException {
    ExchangeUserSetting exchangeUserSetting = getExchangeSetting(userIdentityId);
    try (ExchangeService exchangeService = ExchangeConnectorUtils.connectExchangeServer(exchangeUserSetting)) {
      ItemView view = new ItemView(100);

      ZonedDateTime startZonedDateTime = AgendaDateUtils.parseAllDayDateToZonedDateTime(start);
      SearchFilter exchangeStartSearchFilter =
                                             new SearchFilter.IsGreaterThanOrEqualTo(AppointmentSchema.Start,
                                                                                     AgendaDateUtils.toDate(startZonedDateTime));
      ZonedDateTime endZonedDatetime = AgendaDateUtils.parseAllDayDateToZonedDateTime(end).plusDays(1);// We
                                                                                                       // have
                                                                                                       // added
                                                                                                       // on
                                                                                                       // day
                                                                                                       // in
                                                                                                       // order
                                                                                                       // to
                                                                                                       // get
                                                                                                       // events
                                                                                                       // of
                                                                                                       // the
                                                                                                       // end
                                                                                                       // date
                                                                                                       // day
      SearchFilter exchangeEndSearchFilter = new SearchFilter.IsLessThanOrEqualTo(AppointmentSchema.End,
                                                                                  AgendaDateUtils.toDate(endZonedDatetime));

      SearchFilter exchangeEventsSearchFilter = new SearchFilter.SearchFilterCollection(LogicalOperator.And,
                                                                                        exchangeStartSearchFilter,
                                                                                        exchangeEndSearchFilter);
      FindItemsResults<Item> exchangeEventsItems = exchangeService.findItems(WellKnownFolderName.Calendar,
                                                                             exchangeEventsSearchFilter,
                                                                             view);
      List<EventEntity> exchangeEvents = new ArrayList<>();
      for (Item exchangeEventItem : exchangeEventsItems) {
        EventEntity exchangeEvent = new EventEntity();
        exchangeEvent.setRemoteId(String.valueOf(exchangeEventItem.getId()));
        exchangeEvent.setSummary(exchangeEventItem.getSubject());
        Map<PropertyDefinition, Object> exchangeEventItemProperties = exchangeEventItem.getPropertyBag().getProperties();

        Date exchangeEventStartDate =
                                    (Date) Objects.requireNonNull(exchangeEventItemProperties.entrySet()
                                                                                             .stream()
                                                                                             .filter(exchangeEventItemProperty -> exchangeEventItemProperty.getKey()
                                                                                                                                                           .getUri()
                                                                                                                                                           .equals(ExchangeConnectorUtils.EXCHANGE_APPOINTMENT_SCHEMA_START))
                                                                                             .findFirst()
                                                                                             .orElse(null))
                                                  .getValue();
        ZonedDateTime exchangeEventStartDateTime = AgendaDateUtils.fromDate(exchangeEventStartDate)
                                                                  .withZoneSameInstant(userTimeZone);
        exchangeEvent.setStart(AgendaDateUtils.toRFC3339Date(exchangeEventStartDateTime));

        Date exchangeEventEndDate =
                                  (Date) Objects.requireNonNull(exchangeEventItemProperties.entrySet()
                                                                                           .stream()
                                                                                           .filter(exchangeEventItemProperty -> exchangeEventItemProperty.getKey()
                                                                                                                                                         .getUri()
                                                                                                                                                         .equals(ExchangeConnectorUtils.EXCHANGE_APPOINTMENT_SCHEMA_END))
                                                                                           .findFirst()
                                                                                           .orElse(null))
                                                .getValue();
        ZonedDateTime exchangeEventEndDateTime = AgendaDateUtils.fromDate(exchangeEventEndDate).withZoneSameInstant(userTimeZone);
        exchangeEvent.setEnd(AgendaDateUtils.toRFC3339Date(exchangeEventEndDateTime));
        exchangeEvents.add(exchangeEvent);
      }
      return exchangeEvents;
    } catch (ServiceLocalException e) {
      LOG.error("User {} is not allowed to get exchange events informations",userIdentityId,e);
      throw new IllegalAccessException("User '" + userIdentityId + "' is not allowed to get exchange events informations");
    } catch (Exception e) {
      LOG.error("User {} is not allowed to connect to exchange server",userIdentityId,e);
      throw new IllegalAccessException("User '" + userIdentityId + "' is not allowed to connect to exchange server");
    }
  }

  @Override
  public void pushEventToExchange(long userIdentityId, EventEntity event, ZoneId userTimeZone) throws IllegalAccessException {
    ExchangeUserSetting exchangeUserSetting = getExchangeSetting(userIdentityId);
    try (ExchangeService exchangeService = ExchangeConnectorUtils.connectExchangeServer(exchangeUserSetting)) {
      RemoteEvent remoteEvent = agendaRemoteEventService.findRemoteEvent(event.getId(), userIdentityId);
      if (remoteEvent == null) {
        Appointment appointment = new Appointment(exchangeService);
        appointment.setSubject(event.getSummary());
        ZonedDateTime startDate = AgendaDateUtils.parseRFC3339ToZonedDateTime(event.getStart(), userTimeZone);
        ZonedDateTime endDate = AgendaDateUtils.parseRFC3339ToZonedDateTime(event.getEnd(), userTimeZone);
        appointment.setStart(AgendaDateUtils.toDate(startDate));
        appointment.setEnd(AgendaDateUtils.toDate(endDate));
        appointment.save(new FolderId(WellKnownFolderName.Calendar), SendInvitationsMode.SendToAllAndSaveCopy);
        remoteEvent = new RemoteEvent();
        remoteEvent.setIdentityId(userIdentityId);
        remoteEvent.setEventId(event.getId());
        remoteEvent.setRemoteProviderId(event.getRemoteProviderId());
        remoteEvent.setRemoteProviderName(event.getRemoteProviderName());
        remoteEvent.setRemoteId(String.valueOf(appointment.getId()));
        agendaRemoteEventService.saveRemoteEvent(remoteEvent);
      } else {
        ItemId itemId = new ItemId(remoteEvent.getRemoteId());
        Appointment appointment = Appointment.bind(exchangeService, itemId);
        appointment.setSubject(event.getSummary());
        ZonedDateTime startDate = AgendaDateUtils.parseRFC3339ToZonedDateTime(event.getStart(), userTimeZone);
        ZonedDateTime endDate = AgendaDateUtils.parseRFC3339ToZonedDateTime(event.getEnd(), userTimeZone);
        appointment.setStart(AgendaDateUtils.toDate(startDate));
        appointment.setEnd(AgendaDateUtils.toDate(endDate));
        appointment.update(ConflictResolutionMode.AlwaysOverwrite, SendInvitationsOrCancellationsMode.SendToAllAndSaveCopy);
      }
    } catch (ServiceLocalException e) {
      LOG.error("User {} is not allowed to push exchange event informations",userIdentityId,e);
      throw new IllegalAccessException("User '" + userIdentityId + "' is not allowed to push exchange event informations");
    } catch (Exception e) {
      LOG.error("User {} is not allowed to connect to exchange server",userIdentityId,e);
      throw new IllegalAccessException("User '" + userIdentityId + "' is not allowed to connect to exchange server");
    }
  }

  @Override
  public void deleteExchangeEvent(long userIdentityId, long eventId) throws IllegalAccessException {
    RemoteEvent remoteEvent = agendaRemoteEventService.findRemoteEvent(eventId, userIdentityId);
    ExchangeUserSetting exchangeUserSetting = getExchangeSetting(userIdentityId);
    try (ExchangeService exchangeService = ExchangeConnectorUtils.connectExchangeServer(exchangeUserSetting)) {
      ItemId itemId = new ItemId(remoteEvent.getRemoteId());
      Appointment appointment = Appointment.bind(exchangeService, itemId);
      appointment.delete(DeleteMode.MoveToDeletedItems);
      exchangeConnectorStorage.deleteRemoteEvent(eventId, userIdentityId);
    } catch (ServiceLocalException e) {
      LOG.error("User {} is not allowed to remove remote exchange event informations",userIdentityId,e);
      throw new IllegalAccessException("User '" + userIdentityId
          + "' is not allowed to remove remote exchange event informations");
    } catch (Exception e) {
      LOG.error("User {} is not allowed to connect to exchange server",userIdentityId,e);
      throw new IllegalAccessException("User '" + userIdentityId + "' is not allowed to connect to exchange server");
    }
  }
}
