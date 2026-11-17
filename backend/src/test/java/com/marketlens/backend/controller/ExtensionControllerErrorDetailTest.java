package com.marketlens.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExtensionControllerErrorDetailTest {

    @Test
    void exposesValidationReasonWhenProcessingFailsWith422() {
        // No dependencias: solo se prueba el manejador de errores HTTP.
        ExtensionController controller = new ExtensionController(
                null, null, null, null, null);

        ResponseEntity<ProblemDetail> response = controller.explainImportFailure(
                new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Hay una inconsistencia entre los identificadores."));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Hay una inconsistencia entre los identificadores.",
                response.getBody().getDetail());
    }

    @Test
    void keepsReasonForOtherValidationErrors() {
        ExtensionController controller = new ExtensionController(
                null, null, null, null, null);

        ResponseEntity<ProblemDetail> response = controller.explainImportFailure(
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La URL no corresponde al marketplace indicado."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("La URL no corresponde al marketplace indicado.",
                response.getBody().getDetail());
    }
}
