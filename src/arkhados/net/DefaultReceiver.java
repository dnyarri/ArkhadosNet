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

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.network.HostedConnection;
import com.jme3.network.Message;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultReceiver extends AbstractAppState implements Receiver {

    private static final Logger logger =
            Logger.getLogger(Receiver.class.getName());

    static {
        logger.setLevel(Level.SEVERE);
    }
    private final List<CommandHandler> handlers = new ArrayList<>();
    private Application app;
    private int lastReceivedOrderNum = -1;
    private final Map<HostedConnection, Integer> lastReceivedOrderNumMap =
            new HashMap<>();

    @Override
    public void registerCommandHandler(CommandHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Null CommandHandlers are not"
                    + " accepted");
        }
        handlers.add(handler);
    }

    @Override
    public boolean removeCommandHandler(CommandHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Null CommandHandlers are not"
                    + " accepted");
        }

        return handlers.remove(handler);
    }

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        this.app = app;
    }

    private void ack(Object source, int otmId) {
        Ack ack = new Ack(otmId);
        Sender sender = app.getStateManager().getState(Sender.class);
        if (sender.isClient()) {
            sender.addCommand(ack);
        } else {
            ((ServerSender) sender).addCommandForSingle(ack,
                    (HostedConnection) source);
        }
    }

    @Override
    public void messageReceived(Object source, Message m) {
        OneTrueMessage otp = (OneTrueMessage) m;

        if (otp.getOrderNum() < getLastReceivedOrderNum(source)) {
            return;
        }

        if (!otp.getGuaranteed().isEmpty()) {
            handleGuaranteed(source, otp);
        }

        setLastReceivedOrderNum(source, otp.getOrderNum());

        handleUnreliable(source, otp);
    }

    private int getLastReceivedOrderNum(Object source) {

        Sender sender;
        try {
            // FIXME: NPE here
            sender = app.getStateManager().getState(Sender.class);
        } catch (NullPointerException ex) {
            logger.log(Level.WARNING, "", ex);
            throw ex;
        }
        if (sender.isClient()) {
            return lastReceivedOrderNum;
        } else {
            return lastReceivedOrderNumMap.get((HostedConnection) source);
        }
    }

    private void setLastReceivedOrderNum(Object source, int num) {
        Sender sender = app.getStateManager().getState(Sender.class);
        if (sender.isClient()) {
            lastReceivedOrderNum = num;
        } else {
            lastReceivedOrderNumMap.put((HostedConnection) source, num);
        }
    }

    private void handleGuaranteed(Object source, OneTrueMessage otp) {
        int lastReceivedOrderNumber = getLastReceivedOrderNum(source);

        for (OtmIdCommandListPair otmIdCommandListPair : otp.getGuaranteed()) {
            if (otmIdCommandListPair.getOtmId() <= lastReceivedOrderNumber) {
                continue;
            }

            // TODO: Investigate why ConcurrentModificationException happens here so often
            // NOTE: It might have something / much to do with ACK
            // NOTE: Or perhaps handlers-list is being changed
            for (Command command : otmIdCommandListPair.getCommandList()) {
                for (CommandHandler commandHandler : handlers) {
                    commandHandler.readGuaranteed(source, command);
                }
            }
        }

        ack(source, otp.getOrderNum());
    }

    private void handleUnreliable(Object source, OneTrueMessage otp) {
        // FIXME: ConcurrentModificatinException sometimes happens right here
        for (Command command : otp.getUnreliables()) {
            for (CommandHandler commandHandler : handlers) {
                commandHandler.readUnreliable(source, command);
            }
        }

    }

    @Override
    public void addConnection(HostedConnection connection) {
        lastReceivedOrderNumMap.put(connection, -1);
    }

    @Override
    public void reset() {
        lastReceivedOrderNumMap.clear();
        lastReceivedOrderNum = -1;
    }
}
