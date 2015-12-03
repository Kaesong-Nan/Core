package org.bukkit.plugin.messaging;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@link Messenger}的标准实现.
 */
public class StandardMessenger implements Messenger {
    private final Map<String, Set<PluginMessageListenerRegistration>> incomingByChannel = new HashMap<String, Set<PluginMessageListenerRegistration>>();
    private final Map<Plugin, Set<PluginMessageListenerRegistration>> incomingByPlugin = new HashMap<Plugin, Set<PluginMessageListenerRegistration>>();
    private final Map<String, Set<Plugin>> outgoingByChannel = new HashMap<String, Set<Plugin>>();
    private final Map<Plugin, Set<String>> outgoingByPlugin = new HashMap<Plugin, Set<String>>();
    private final Object incomingLock = new Object();
    private final Object outgoingLock = new Object();

    private void addToOutgoing(Plugin plugin, String channel) {
        synchronized (outgoingLock) {
            Set<Plugin> plugins = outgoingByChannel.get(channel);
            Set<String> channels = outgoingByPlugin.get(plugin);

            if (plugins == null) {
                plugins = new HashSet<Plugin>();
                outgoingByChannel.put(channel, plugins);
            }

            if (channels == null) {
                channels = new HashSet<String>();
                outgoingByPlugin.put(plugin, channels);
            }

            plugins.add(plugin);
            channels.add(channel);
        }
    }

    private void removeFromOutgoing(Plugin plugin, String channel) {
        synchronized (outgoingLock) {
            Set<Plugin> plugins = outgoingByChannel.get(channel);
            Set<String> channels = outgoingByPlugin.get(plugin);

            if (plugins != null) {
                plugins.remove(plugin);

                if (plugins.isEmpty()) {
                    outgoingByChannel.remove(channel);
                }
            }

            if (channels != null) {
                channels.remove(channel);

                if (channels.isEmpty()) {
                    outgoingByChannel.remove(channel);
                }
            }
        }
    }

    private void removeFromOutgoing(Plugin plugin) {
        synchronized (outgoingLock) {
            Set<String> channels = outgoingByPlugin.get(plugin);

            if (channels != null) {
                String[] toRemove = channels.toArray(new String[0]);

                outgoingByPlugin.remove(plugin);

                for (String channel : toRemove) {
                    removeFromOutgoing(plugin, channel);
                }
            }
        }
    }

    private void addToIncoming(PluginMessageListenerRegistration registration) {
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByChannel.get(registration.getChannel());

            if (registrations == null) {
                registrations = new HashSet<PluginMessageListenerRegistration>();
                incomingByChannel.put(registration.getChannel(), registrations);
            } else {
                if (registrations.contains(registration)) {
                    throw new IllegalArgumentException("This registration already exists");
                }
            }

            registrations.add(registration);

            registrations = incomingByPlugin.get(registration.getPlugin());

            if (registrations == null) {
                registrations = new HashSet<PluginMessageListenerRegistration>();
                incomingByPlugin.put(registration.getPlugin(), registrations);
            } else {
                if (registrations.contains(registration)) {
                    throw new IllegalArgumentException("This registration already exists");
                }
            }

            registrations.add(registration);
        }
    }

    private void removeFromIncoming(PluginMessageListenerRegistration registration) {
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByChannel.get(registration.getChannel());

            if (registrations != null) {
                registrations.remove(registration);

                if (registrations.isEmpty()) {
                    incomingByChannel.remove(registration.getChannel());
                }
            }

            registrations = incomingByPlugin.get(registration.getPlugin());

            if (registrations != null) {
                registrations.remove(registration);

                if (registrations.isEmpty()) {
                    incomingByPlugin.remove(registration.getPlugin());
                }
            }
        }
    }

    private void removeFromIncoming(Plugin plugin, String channel) {
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByPlugin.get(plugin);

            if (registrations != null) {
                PluginMessageListenerRegistration[] toRemove = registrations.toArray(new PluginMessageListenerRegistration[0]);

                for (PluginMessageListenerRegistration registration : toRemove) {
                    if (registration.getChannel().equals(channel)) {
                        removeFromIncoming(registration);
                    }
                }
            }
        }
    }

    private void removeFromIncoming(Plugin plugin) {
        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByPlugin.get(plugin);

            if (registrations != null) {
                PluginMessageListenerRegistration[] toRemove = registrations.toArray(new PluginMessageListenerRegistration[0]);

                incomingByPlugin.remove(plugin);

                for (PluginMessageListenerRegistration registration : toRemove) {
                    removeFromIncoming(registration);
                }
            }
        }
    }

    public boolean isReservedChannel(String channel) {
        validateChannel(channel);

        return channel.equals("REGISTER") || channel.equals("UNREGISTER");
    }

    public void registerOutgoingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        validateChannel(channel);
        if (isReservedChannel(channel)) {
            throw new ReservedChannelException(channel);
        }

        addToOutgoing(plugin, channel);
    }

    public void unregisterOutgoingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        validateChannel(channel);

        removeFromOutgoing(plugin, channel);
    }

    public void unregisterOutgoingPluginChannel(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        removeFromOutgoing(plugin);
    }

    public PluginMessageListenerRegistration registerIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        validateChannel(channel);
        if (isReservedChannel(channel)) {
            throw new ReservedChannelException(channel);
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        PluginMessageListenerRegistration result = new PluginMessageListenerRegistration(this, plugin, channel, listener);

        addToIncoming(result);

        return result;
    }

    public void unregisterIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        validateChannel(channel);

        removeFromIncoming(new PluginMessageListenerRegistration(this, plugin, channel, listener));
    }

    public void unregisterIncomingPluginChannel(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        validateChannel(channel);

        removeFromIncoming(plugin, channel);
    }

    public void unregisterIncomingPluginChannel(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        removeFromIncoming(plugin);
    }

    public Set<String> getOutgoingChannels() {
        synchronized (outgoingLock) {
            Set<String> keys = outgoingByChannel.keySet();
            return ImmutableSet.copyOf(keys);
        }
    }

    public Set<String> getOutgoingChannels(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        synchronized (outgoingLock) {
            Set<String> channels = outgoingByPlugin.get(plugin);

            if (channels != null) {
                return ImmutableSet.copyOf(channels);
            } else {
                return ImmutableSet.of();
            }
        }
    }

    public Set<String> getIncomingChannels() {
        synchronized (incomingLock) {
            Set<String> keys = incomingByChannel.keySet();
            return ImmutableSet.copyOf(keys);
        }
    }

    public Set<String> getIncomingChannels(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByPlugin.get(plugin);

            if (registrations != null) {
                Builder<String> builder = ImmutableSet.builder();

                for (PluginMessageListenerRegistration registration : registrations) {
                    builder.add(registration.getChannel());
                }

                return builder.build();
            } else {
                return ImmutableSet.of();
            }
        }
    }

    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByPlugin.get(plugin);

            if (registrations != null) {
                return ImmutableSet.copyOf(registrations);
            } else {
                return ImmutableSet.of();
            }
        }
    }

    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(String channel) {
        validateChannel(channel);

        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByChannel.get(channel);

            if (registrations != null) {
                return ImmutableSet.copyOf(registrations);
            } else {
                return ImmutableSet.of();
            }
        }
    }

    public Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        validateChannel(channel);

        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByPlugin.get(plugin);

            if (registrations != null) {
                Builder<PluginMessageListenerRegistration> builder = ImmutableSet.builder();

                for (PluginMessageListenerRegistration registration : registrations) {
                    if (registration.getChannel().equals(channel)) {
                        builder.add(registration);
                    }
                }

                return builder.build();
            } else {
                return ImmutableSet.of();
            }
        }
    }

    public boolean isRegistrationValid(PluginMessageListenerRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("Registration cannot be null");
        }

        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByPlugin.get(registration.getPlugin());

            if (registrations != null) {
                return registrations.contains(registration);
            }

            return false;
        }
    }

    public boolean isIncomingChannelRegistered(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        validateChannel(channel);

        synchronized (incomingLock) {
            Set<PluginMessageListenerRegistration> registrations = incomingByPlugin.get(plugin);

            if (registrations != null) {
                for (PluginMessageListenerRegistration registration : registrations) {
                    if (registration.getChannel().equals(channel)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public boolean isOutgoingChannelRegistered(Plugin plugin, String channel) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        validateChannel(channel);

        synchronized (outgoingLock) {
            Set<String> channels = outgoingByPlugin.get(plugin);

            if (channels != null) {
                return channels.contains(channel);
            }

            return false;
        }
    }

    public void dispatchIncomingMessage(Player source, String channel, byte[] message) {
        if (source == null) {
            throw new IllegalArgumentException("Player source cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        validateChannel(channel);

        Set<PluginMessageListenerRegistration> registrations = getIncomingChannelRegistrations(channel);

        for (PluginMessageListenerRegistration registration : registrations) {
            registration.getListener().onPluginMessageReceived(channel, source, message);
        }
    }

    /**
     * 验证一个插件通道(Plugin Channel)的名称.
     * <p>
     * 原文：Validates a Plugin Channel name.
     *
     * @param channel 要验证的通道名称
     */
    public static void validateChannel(String channel) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }
        if (channel.length() > Messenger.MAX_CHANNEL_SIZE) {
            throw new ChannelNameTooLongException(channel);
        }
    }

    /**
     * 验证插件消息(Plugin Message)的输入，确保这些参数都是有效的.
     * <p>
     * 原文：Validates the input of a Plugin Message, ensuring the arguments are all
     * valid.
     *
     * @param messenger 用于验证的MessengerMessenger to use for validatio
     * @param source 信息的来源插件
     * @param channel 通过什么插件通道(Plugin Channel)来发送消息
     * @param message 发送的原始消息的有效载荷
     * @throws IllegalArgumentException 如果源插件被禁用则抛出
     * @throws IllegalArgumentException 如果参数source,channel或message为null则抛出
     * @throws MessageTooLargeException 如果消息过大则抛出
     * @throws ChannelNameTooLongException 如果通道名称过长则抛出
     * @throws ChannelNotRegisteredException 如果这个通道不是为这个插件注册的则抛出
     */
    public static void validatePluginMessage(Messenger messenger, Plugin source, String channel, byte[] message) {
        if (messenger == null) {
            throw new IllegalArgumentException("Messenger cannot be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("Plugin source cannot be null");
        }
        if (!source.isEnabled()) {
            throw new IllegalArgumentException("Plugin must be enabled to send messages");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (!messenger.isOutgoingChannelRegistered(source, channel)) {
            throw new ChannelNotRegisteredException(channel);
        }
        if (message.length > Messenger.MAX_MESSAGE_SIZE) {
            throw new MessageTooLargeException(message);
        }
        validateChannel(channel);
    }
}