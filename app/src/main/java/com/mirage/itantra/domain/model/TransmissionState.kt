package com.mirage.itantra.domain.model

/**
 * Current state of the speech/transmission pipeline.
 */
enum class TransmissionState {
    /** Ready for input */
    IDLE,
    /** PTT held â€” microphone active, capturing audio */
    RECORDING,
    /** PTT released â€” running STT inference */
    PROCESSING_STT,
    /** STT complete â€” encoding iTantra packet */
    ENCODING,
    /** Packet encoded â€” transmitting over transport */
    TRANSMITTING,
    /** Incoming packet received â€” decoding */
    RECEIVING,
    /** Received text â€” running TTS synthesis */
    PROCESSING_TTS,
    /** TTS complete â€” playing audio through speaker */
    PLAYING,
    /** Pipeline error */
    ERROR
}
