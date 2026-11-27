package utp.mastertech.mastertech.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.dto.CheckoutRequest;
import utp.mastertech.mastertech.dto.ConfirmarPagoRequest;
import utp.mastertech.mastertech.dto.CotizacionDTO;
import utp.mastertech.mastertech.dto.CotizacionRequest;
import utp.mastertech.mastertech.dto.PuntoRepartoDTO;
import utp.mastertech.mastertech.dto.VentaDTO;
import utp.mastertech.mastertech.mapper.EnvioMapper;
import utp.mastertech.mastertech.mapper.VentaMapper;
import utp.mastertech.mastertech.service.PedidoService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pedidos")
public class PedidoRestController {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private VentaMapper ventaMapper;

    @Autowired
    private EnvioMapper envioMapper;

    /** Sedes donde se puede recoger el pedido. */
    @GetMapping("/puntos-recojo")
    public List<PuntoRepartoDTO> puntosRecojo() {
        return pedidoService.puntosRecojo().stream().map(envioMapper::toDTO).toList();
    }

    /** Costo de envío y fecha estimada para un carrito y una ubicación (no crea nada). */
    @PostMapping("/cotizar")
    public CotizacionDTO cotizar(@Valid @RequestBody CotizacionRequest request) {
        return pedidoService.cotizar(request.getItems(), request.getTipoEntrega(), request.getLatitud(),
                request.getLongitud(), request.getPuntoRepartoId());
    }

    /** Crea la sesión de Stripe Checkout y devuelve su URL. */
    @PostMapping("/checkout")
    public Map<String, String> checkout(@Valid @RequestBody CheckoutRequest request,
                                        Authentication authentication) {
        String url = pedidoService.crearCheckout(authentication.getName(), request.getItems(),
                request.getTipoEntrega(), request.getEntrega(), request.getPuntoRepartoId());
        return Map.of("url", url);
    }

    /** Se llama al volver de Stripe: verifica el pago y registra la venta. */
    @PostMapping("/confirmar")
    public VentaDTO confirmar(@Valid @RequestBody ConfirmarPagoRequest request,
                              Authentication authentication) {
        return ventaMapper.toDTO(pedidoService.confirmar(authentication.getName(), request.getSessionId()));
    }
}
