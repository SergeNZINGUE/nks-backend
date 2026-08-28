package bf.laterrasse.nks.dto.sms;

import java.util.List;

public record SmsBulkResponse(
        int nbEnvoyes,
        int nbEchecs,
        List<EchecSms> echecs
) {
    public record EchecSms(String telephone, String erreur) {}
}
