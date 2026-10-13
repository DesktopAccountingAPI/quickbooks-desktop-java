package com.desktopaccountingapi.quickbooksdesktop.core;

/**
 * The argument that confirms a destructive request (a list-object delete, a merge or a custom field
 * definition delete): {@code new CustomerDeleteParams(Confirm.YES)}. It is the API's
 * {@code confirm=true}, required by the constructor so the request cannot be sent by accident.
 */
public enum Confirm {
    /** The change is intended: sends {@code confirm: true}. */
    YES
}
