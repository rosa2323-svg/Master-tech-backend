package utp.mastertech.mastertech.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private PedidoService pedidoService;

    @InjectMocks
    private StripeWebhookService service;

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test");
    }

    private Event evento(String tipo, String sessionId) {
        Event e = mock(Event.class);
        when(e.getType()).thenReturn(tipo);
        if (sessionId != null) {
            Session s = mock(Session.class);
            when(s.getId()).thenReturn(sessionId);
            EventDataObjectDeserializer d = mock(EventDataObjectDeserializer.class);
            when(d.getObject()).thenReturn(Optional.of(s));
            when(e.getDataObjectDeserializer()).thenReturn(d);
        }
        return e;
    }

    @Test
    void estaHabilitadoSoloConSecreto() {
        assertTrue(service.habilitado());
        ReflectionTestUtils.setField(service, "webhookSecret", " ");
        assertFalse(service.habilitado());
    }

    @Test
    void sesionCompletadaRegistraElPago() {
        Event evento = evento("checkout.session.completed", "cs_1");
        try (MockedStatic<Webhook> w = mockStatic(Webhook.class)) {
            w.when(() -> Webhook.constructEvent("{}", "t=1,v1=x", "whsec_test")).thenReturn(evento);

            service.procesar("{}", "t=1,v1=x");
        }

        verify(pedidoService).registrarPagoDeSesion("cs_1");
    }

    @Test
    void sesionExpiradaNoHaceNada() {
        Event evento = evento("checkout.session.expired", "cs_1");
        try (MockedStatic<Webhook> w = mockStatic(Webhook.class)) {
            w.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(evento);

            service.procesar("{}", "firma");
        }

        verifyNoInteractions(pedidoService);
    }

    @Test
    void otrosEventosSeIgnoranConExito() {
        Event evento = evento("charge.succeeded", null);
        try (MockedStatic<Webhook> w = mockStatic(Webhook.class)) {
            w.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(evento);

            assertDoesNotThrow(() -> service.procesar("{}", "firma"));
        }

        verifyNoInteractions(pedidoService);
    }

    @Test
    void firmaInvalidaLanzaExcepcionYNoProcesa() {
        try (MockedStatic<Webhook> w = mockStatic(Webhook.class)) {
            w.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("mala", "firma"));

            assertThrows(StripeWebhookService.FirmaInvalidaException.class, () -> service.procesar("{}", "firma"));
        }

        verifyNoInteractions(pedidoService);
    }
}
