package utp.mastertech.mastertech.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import utp.mastertech.mastertech.service.StripeWebhookService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripeWebhookService webhookService;

    @InjectMocks
    private StripeWebhookController controller;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void conFirmaValidaResponde200() throws Exception {
        when(webhookService.habilitado()).thenReturn(true);

        mvc.perform(post("/api/stripe/webhook").header("Stripe-Signature", "t=1,v1=x")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isOk());

        verify(webhookService).procesar("{\"id\":\"evt_1\"}", "t=1,v1=x");
    }

    @Test
    void sinSecretoConfiguradoResponde503() throws Exception {
        when(webhookService.habilitado()).thenReturn(false);

        mvc.perform(post("/api/stripe/webhook").header("Stripe-Signature", "x")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());

        verify(webhookService, never()).procesar(any(), any());
    }

    @Test
    void sinEncabezadoDeFirmaResponde400() throws Exception {
        when(webhookService.habilitado()).thenReturn(true);

        mvc.perform(post("/api/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(webhookService, never()).procesar(any(), any());
    }

    @Test
    void conFirmaInvalidaResponde400() throws Exception {
        when(webhookService.habilitado()).thenReturn(true);
        doThrow(new StripeWebhookService.FirmaInvalidaException("Firma de Stripe inválida"))
                .when(webhookService).procesar(any(), any());

        mvc.perform(post("/api/stripe/webhook").header("Stripe-Signature", "mala")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
