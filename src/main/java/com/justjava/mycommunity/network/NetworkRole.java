package com.justjava.mycommunity.network;

/**
 * Roles a user can hold within a Network.
 *
 *   OWNER  – created the network, full control
 *   ADMIN  – can manage members and moderate content
 *   MEMBER – standard participant: can post, comment, message
 */
public enum NetworkRole {
    OWNER,
    ADMIN,
    MEMBER
}

