package com.keyStone.Playroom021.security;

import com.keyStone.Playroom021.entity.Customer;
import com.keyStone.Playroom021.entity.Role;
import com.keyStone.Playroom021.entity.User;
import org.springframework.security.access.AccessDeniedException;

/**
 * Answers "which customer's data may this caller see?" from the authenticated
 * principal only - never from request input.
 *
 * Staff roles (MANAGER, DISPATCHER, TECHNICIAN) are not customer-scoped and get
 * {@code null}. A CUSTOMER is scoped to the id of the Customer they are linked to.
 */
public final class CallerScope {

    private CallerScope() {
    }

    /** @return the caller's own customer id if the caller is a CUSTOMER, otherwise null (unrestricted). */
    public static Long customerIdOrNull(CustomUserDetails principal) {
        User user = principal.getUser();
        if (user.getRole() != Role.CUSTOMER) {
            return null;
        }
        Customer customer = user.getCustomer();
        if (customer == null) {
            // Fail closed: a CUSTOMER account with no linked customer sees nothing.
            throw new AccessDeniedException("This account has no linked customer profile");
        }
        return customer.getId();
    }

    /** @return the caller's own user id if the caller is a TECHNICIAN, otherwise null (unrestricted). */
    public static Long technicianIdOrNull(CustomUserDetails principal) {
        User user = principal.getUser();
        return user.getRole() == Role.TECHNICIAN ? user.getId() : null;
    }
}
