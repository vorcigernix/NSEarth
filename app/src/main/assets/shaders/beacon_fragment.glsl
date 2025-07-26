precision mediump float;

uniform float u_Time;
uniform vec4 u_Color;
uniform float u_Pulse;
varying float v_Y; // The y-coordinate of the vertex, from 0 (base) to height (apex)

void main() {
    // Normalize y to be in the 0.0 to 1.0 range
    float normalizedY = v_Y / 0.5; // 0.5 is the height of the beacon

    // --- Core of the Beacon ---
    // Create a bright, sharp core that is strongest at the center and fades out
    float coreIntensity = 1.0 - normalizedY;
    coreIntensity = pow(coreIntensity, 10.0) * u_Pulse; // Use pow for a sharper falloff

    // --- Outer Glow ---
    // Create a softer, wider glow that fades more gently
    float glowIntensity = 1.0 - normalizedY;
    glowIntensity = pow(glowIntensity, 3.0) * 0.5 * u_Pulse; // Softer falloff

    // --- Color ---
    // Use a vibrant yellow/white color for the beacon
    vec3 beaconColor = vec3(1.0, 1.0, 0.8);

    // --- Combine and Set Final Color ---
    // The final color is the core + glow. The alpha controls the transparency.
    // The additive blending will make the colors stack and appear to glow.
    vec3 finalColor = beaconColor * coreIntensity + beaconColor * glowIntensity;
    float alpha = (coreIntensity + glowIntensity) * u_Color.a;

    gl_FragColor = vec4(finalColor, alpha);
}
