package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.enums.Enums.CanalNotification;
import bf.laterrasse.nks.domain.enums.Enums.StatutEnvoiNotification;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.gateway.email.EmailGateway;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.gateway.sms.WhatsappGateway;
import bf.laterrasse.nks.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the WhatsApp-first / SMS-fallback priority introduced in envoyerSms(): WhatsApp is
 * attempted synchronously first (best-effort) and only on failure does the pre-existing SMS
 * flow (persisted + async retry) run.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceWhatsappTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private AsyncNotificationSender asyncSender;
    @Mock private WhatsappGateway whatsappGateway;
    @Mock private SmsGateway smsGateway;
    @Mock private EmailGateway emailGateway;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, asyncSender, whatsappGateway, smsGateway, emailGateway);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("envoyerSms() succeeds via WhatsApp: persists canal=WHATSAPP, statut=ENVOYE, never touches the SMS fallback")
    void envoyerSms_whatsappReussit_persisteCanalWhatsappSansFallbackSms() {
        when(whatsappGateway.envoyer(anyString(), anyString(), anyList())).thenReturn("WA-SID-123");

        service.envoyerSms(null, "+22670000001", TypeNotification.BILLET_EMIS, "Voici ton billet");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getCanal()).isEqualTo(CanalNotification.WHATSAPP);
        assertThat(saved.getStatutEnvoi()).isEqualTo(StatutEnvoiNotification.ENVOYE);
        assertThat(saved.getReferenceExterne()).isEqualTo("WA-SID-123");
        verify(asyncSender, never()).tenterEnvoi(any());
    }

    @Test
    @DisplayName("envoyerSms() falls back to SMS when WhatsApp fails: persists canal=SMS unchanged, existing retry flow untouched")
    void envoyerSms_whatsappEchoue_bascculeSurFlotSmsExistant() {
        when(whatsappGateway.envoyer(anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("HDR Stream indisponible"));
        TransactionSynchronizationManager.initSynchronization();

        service.envoyerSms(null, "+22670000002", TypeNotification.BILLET_EMIS, "Voici ton billet");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getCanal()).isEqualTo(CanalNotification.SMS);
        assertThat(saved.getStatutEnvoi()).isEqualTo(StatutEnvoiNotification.EN_ATTENTE);
    }
}
