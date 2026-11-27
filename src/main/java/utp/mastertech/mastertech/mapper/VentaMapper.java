package utp.mastertech.mastertech.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import utp.mastertech.mastertech.dto.VentaDTO;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.service.VentaService;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
public class VentaMapper {

    @Autowired
    private DetalleVentaMapper detalleVentaMapper;

    // Entidad → DTO
    public VentaDTO toDTO(Venta venta) {
        if (venta == null) return null;

        VentaDTO dto = new VentaDTO();
        dto.setId(venta.getId());
        dto.setFecha(venta.getFecha());
        dto.setTotal(venta.getTotal());
        dto.setCantidadItems(venta.getCantidadItems());
        dto.setNumeroOrden(venta.getNumeroOrden());
        dto.setEstado(estadoVisible(venta.getEstado()));
        dto.setSiguienteEstado(VentaService.siguienteEstado(venta.getTipoEntrega(), venta.getEstado()));

        if (venta.getUsuario() != null) {
            dto.setUsuarioId(venta.getUsuario().getId());
            dto.setUsuarioNombre(venta.getUsuario().getNombre());
            dto.setUsuarioEmail(venta.getUsuario().getEmail());
            dto.setUsuarioDireccion(venta.getUsuario().getDireccion());
            dto.setUsuarioTelefono(venta.getUsuario().getNumeroTelefono());
        }

        dto.setTipoEntrega(venta.getTipoEntrega());
        dto.setCostoEnvio(venta.getCostoEnvio());
        // Solo la dirección que el cliente eligió al pagar. La del perfil va aparte (usuarioDireccion): mezclarlas
        // hacía parecer que el pedido iba a la dirección del perfil cuando no se guardó ninguna.
        dto.setDireccionEntrega(venta.getDireccionEntrega());
        dto.setLatitudEntrega(venta.getLatitudEntrega());
        dto.setLongitudEntrega(venta.getLongitudEntrega());
        dto.setDistanciaKm(venta.getDistanciaKm());
        dto.setFechaEstimadaEntrega(venta.getFechaEstimadaEntrega());
        dto.setFechaEntregaReal(venta.getFechaEntregaReal());
        dto.setFechaAnulacion(venta.getFechaAnulacion());
        dto.setMotivoAnulacion(venta.getMotivoAnulacion());
        if (venta.getPuntoReparto() != null) {
            dto.setPuntoRepartoId(venta.getPuntoReparto().getId());
            dto.setPuntoRepartoNombre(venta.getPuntoReparto().getNombre());
        }

        // Mapear los detalles usando DetalleVentaMapper
        if (venta.getDetalles() != null) {
            dto.setDetalles(
                venta.getDetalles().stream()
                    .map(detalleVentaMapper::toDTO)
                    .collect(Collectors.toList())
            );
        } else {
            dto.setDetalles(Collections.emptyList());
        }

        return dto;
    }
    /** Estados del flujo y ANULADA se muestran tal cual; lo anterior a los envíos (p. ej. COMPLETADA) se ve como Pagado. */
    private String estadoVisible(String estado) {
        String e = VentaService.normalizarEstado(estado);
        return VentaService.FLUJO_DESPACHO.contains(e) || VentaService.ANULADA.equals(e) ? e : "Pagado";
    }
}
