package io.izzel.arclight.common.mixin.core.network.protocol.handshake;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientIntentionPacket.class)
public class ClientIntentionPacketMixin {

    @Redirect(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readUtf(I)Ljava/lang/String;"))
    private static String arclight$bungeeHostname(FriendlyByteBuf packetBuffer, int maxLength) {
        try {
            if (packetBuffer.readableBytes() < 1) {
                return "";
            }

            // Read string length first to validate
            int readerIndex = packetBuffer.readerIndex();
            int stringLength = packetBuffer.readVarInt();

            // Check if we have enough bytes for the string
            if (stringLength < 0 || stringLength > packetBuffer.readableBytes()) {
                packetBuffer.readerIndex(readerIndex);
                return "";
            }

            // Reset reader index and read normally
            packetBuffer.readerIndex(readerIndex);
            return packetBuffer.readUtf(Short.MAX_VALUE);
        } catch (Exception e) {
            System.err.println("[Luminara] Error reading hostname from handshake packet: " + e.getMessage());
            return "";
        }
    }
}
