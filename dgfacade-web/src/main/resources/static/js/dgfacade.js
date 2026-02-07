/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * DGFacade UI Scripts
 */

function sendTestRequest() {
    var payload = document.getElementById('testPayload').value;
    var resultDiv = document.getElementById('testResult');
    var responseEl = document.getElementById('testResponse');

    try {
        JSON.parse(payload);
    } catch (e) {
        alert('Invalid JSON: ' + e.message);
        return;
    }

    resultDiv.style.display = 'none';

    fetch('/api/v1/request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: payload
    })
    .then(function(response) { return response.json(); })
    .then(function(data) {
        responseEl.textContent = JSON.stringify(data, null, 2);
        resultDiv.style.display = 'block';
    })
    .catch(function(error) {
        responseEl.textContent = 'Error: ' + error.message;
        resultDiv.style.display = 'block';
    });
}

function testHandler(btn) {
    var type = btn.getAttribute('data-type');
    var payload = {
        apiKey: 'dgf-dev-key-001',
        requestType: type,
        payload: {}
    };

    if (type === 'ARITHMETIC') {
        payload.payload = { operation: 'ADD', operandA: 42, operandB: 18 };
    } else if (type === 'ECHO') {
        payload.payload = { message: 'Hello DGFacade!' };
    }

    fetch('/api/v1/request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(function(response) { return response.json(); })
    .then(function(data) {
        alert('Result: ' + JSON.stringify(data, null, 2));
    })
    .catch(function(error) {
        alert('Error: ' + error.message);
    });
}

// Auto-dismiss alerts after 5 seconds
document.addEventListener('DOMContentLoaded', function() {
    var alerts = document.querySelectorAll('.alert-dismissible');
    alerts.forEach(function(alert) {
        setTimeout(function() {
            var bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            bsAlert.close();
        }, 5000);
    });
});
