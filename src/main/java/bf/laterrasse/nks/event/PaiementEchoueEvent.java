package bf.laterrasse.nks.event;

import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;

import java.util.UUID;

public record PaiementEchoueEvent(UUID paiementId, TypePaiement typePaiement) {
}
