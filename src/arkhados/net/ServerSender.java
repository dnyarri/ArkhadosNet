/*
 * Copyright (c) 2009-2011 William Linna
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'Arkhados' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package arkhados.net;

import com.jme3.network.Filters;
import com.jme3.network.HostedConnection;
import com.jme3.network.Server;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ServerSender extends AbstractSender {

    private final Map<HostedConnection, List<OtmIdCommandListPair>> unconfirmedGuaranteed = new HashMap<>();
    private final Map<HostedConnection, List<Command>> enqueuedGuaranteed = new HashMap<>();
    private final Map<HostedConnection, List<Command>> enqueuedUnreliables = new HashMap<>();
    private final Server server;

    public ServerSender(Server server) {
        this.server = server;
    }

    private void broadcast() {
        try {
            for (HostedConnection connection : enqueuedGuaranteed.keySet()) {
                OneTrueMessage otm = createOneTrueMessage(connection);
                server.broadcast(Filters.in(connection), otm);
            }
        } catch (BufferOverflowException ex) {
            logger.log(Level.SEVERE, "", ex);
            System.exit(1);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "", ex);
        }
    }

    public void addCommandForSingle(Command command,
            HostedConnection connection) {
        if (command.isGuaranteed()) {
            enqueuedGuaranteed.get(connection).add(command);
        } else {
            enqueuedUnreliables.get(connection).add(command);
        }

        setShouldSend(true);
    }

    @Override
    public void addCommand(Command command) {
        addCommand(command, server.getConnections());
    }

    public void addCommand(Command command,
            Collection<HostedConnection> connections) {
        if (command == null) {
            throw new IllegalArgumentException("Null Commands are"
                    + " not allowed");
        }
        setShouldSend(true);
        for (HostedConnection hostedConnection : connections) {
            addCommandForSingle(command, hostedConnection);
        }
    }

    public void addConnection(HostedConnection conn) {
        unconfirmedGuaranteed.put(conn, new ArrayList<>());
        enqueuedGuaranteed.put(conn, new ArrayList<>());
        enqueuedUnreliables.put(conn, new ArrayList<>());
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public boolean isServer() {
        return true;
    }

    @Override
    public void sendMessage() {
        if (!server.isRunning()) {
            return;
        }
        broadcast();
    }

    @Override
    protected List<OtmIdCommandListPair> getGuaranteedForSource(Object source) {
        return unconfirmedGuaranteed.get((HostedConnection) source);
    }

    @Override
    protected List<Command> getEnqueuedGuaranteedForSource(
            HostedConnection connection) {
        return enqueuedGuaranteed.get(connection);
    }

    @Override
    protected List<Command> getEnqueuedUnreliablesForSource(
            HostedConnection connection) {
        return enqueuedUnreliables.get(connection);
    }

    @Override
    public void reset() {
        unconfirmedGuaranteed.clear();
        enqueuedGuaranteed.clear();
        enqueuedUnreliables.clear();
    }

    public Server getServer() {
        return server;
    }

    public void removeConnection(HostedConnection connection) {
        unconfirmedGuaranteed.remove(connection);
        enqueuedGuaranteed.remove(connection);
        enqueuedUnreliables.remove(connection);
    }
}