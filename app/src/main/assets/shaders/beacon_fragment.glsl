precision mediump float;
varying float v_Y;
varying float v_LightIntensity;

void main() {
    float intensity = 1.0 - v_Y / 0.5; // 0.5 is the height of the beacon
    // Make beacon much brighter and more self-illuminated
    vec3 color = vec3(1.0, 1.0, 0.0) * (0.8 + v_LightIntensity * 0.2); // Mostly self-lit
    gl_FragColor = vec4(color * 2.0, intensity); // Double the brightness
} 