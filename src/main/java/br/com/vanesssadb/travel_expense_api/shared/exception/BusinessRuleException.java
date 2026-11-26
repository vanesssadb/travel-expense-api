package br.com.vanesssadb.travel_expense_api.shared.exception;

public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
