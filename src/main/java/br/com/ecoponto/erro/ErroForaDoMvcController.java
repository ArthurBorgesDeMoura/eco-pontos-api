package br.com.ecoponto.erro;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ErroForaDoMvcController implements ErrorController {

    static final String NOSNIFF = "X-Content-Type-Options";

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ErroApi> erro(HttpServletRequest pedido) {
        if (!(pedido.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer status)) {
            return ResponseEntity.status(CodigoDeErro.NAO_ENCONTRADO.status()).header(NOSNIFF, "nosniff")
                    .body(ErroApi.de(CodigoDeErro.NAO_ENCONTRADO));
        }
        return ResponseEntity.status(status).header(NOSNIFF, "nosniff").body(ErroApi.de(CodigoDeErro.doStatus(status)));
    }
}
