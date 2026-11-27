package utp.mastertech.mastertech.mapper;

import org.springframework.stereotype.Component;
import utp.mastertech.mastertech.dto.SolicitudReembolsoDTO;
import utp.mastertech.mastertech.model.SolicitudReembolso;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.service.VentaService;

import java.math.BigDecimal;

@Component
public class ReembolsoMapper {

    public SolicitudReembolsoDTO toDTO(SolicitudReembolso s) {
        SolicitudReembolsoDTO dto = new SolicitudReembolsoDTO();
        dto.setId(s.getId());
        dto.setMotivo(s.getMotivo());
        dto.setEstado(s.getEstado());
        dto.setFechaSolicitud(s.getFechaSolicitud());
        dto.setFechaResolucion(s.getFechaResolucion());
        dto.setNotaAdmin(s.getNotaAdmin());
        if (s.getResueltoPor() != null) {
            dto.setResueltoPorNombre(s.getResueltoPor().getNombre());
        }
        if (s.getUsuario() != null) {
            dto.setUsuarioNombre(s.getUsuario().getNombre());
            dto.setUsuarioEmail(s.getUsuario().getEmail());
        }
        Venta v = s.getVenta();
        if (v != null) {
            dto.setVentaId(v.getId());
            dto.setNumeroOrden(v.getNumeroOrden());
            dto.setEstadoVenta(VentaService.normalizarEstado(v.getEstado()));
            dto.setTipoEntrega(v.getTipoEntrega());
            BigDecimal total = v.getTotal() != null ? v.getTotal() : BigDecimal.ZERO;
            BigDecimal envio = v.getCostoEnvio() != null ? v.getCostoEnvio() : BigDecimal.ZERO;
            dto.setMonto(total.add(envio));
        }
        return dto;
    }
}
