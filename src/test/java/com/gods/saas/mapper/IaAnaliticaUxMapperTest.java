package com.gods.saas.mapper;
import com.gods.saas.domain.dto.response.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class IaAnaliticaUxMapperTest {
 @Test void retainsPreviewAlternativesWithoutInflatingScores() {
 var input=AnalizarImagenResponse.builder()
 .formaRostro(FormaRostroDto.builder().principal("redondo").confianza(.69).build())
 .cabello(CabelloDto.builder().ondulado(OnduladoDto.builder().apto(false).build()).build())
 .recomendaciones(RecomendacionesDto.builder().tintes(List.of()).cortes(List.of(
 cut("BURST_FADE",.89,false),cut("QUIFF",.86,false),cut("CROP",.82,false),
 cut("MID_FADE",.65,true),cut("LOW_FADE",.61,true),cut("TAPER",.60,true),
 cut("BUZZ",.58,true),cut("FADE_MODERNO",.55,true))).build()).build();
 var result=IaAnaliticaUxMapper.toUx(input);
 assertEquals(8,result.getCortesRecomendados().size());
 var preview=result.getCortesRecomendados().stream().filter(c->"MID_FADE".equals(c.getNombre())).findFirst().orElseThrow();
 assertEquals(.65,preview.getScore());
 assertTrue(preview.getVistaGenerativaDisponible());
 assertFalse(result.getCortesRecomendados().get(0).getVistaGenerativaDisponible());
 }
 private CorteRecomendadoDto cut(String code,double score,boolean available) {
 return CorteRecomendadoDto.builder().nombre(code).score(score).vistaGenerativaDisponible(available).razones(List.of("motivo original")).build();
 }
}
