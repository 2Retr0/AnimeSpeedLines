#version 120

#define TWO_PI 6.28318530718

uniform sampler2D DiffuseSampler; // Main texture
uniform float     STime;          // Time in seconds (+ tick delta)
uniform float     Weight;         // Intensity

varying vec2      texCoord;

const float f_rand[18] = float[](
    0.5758, 0.8892, 0.6213, 0.5391, 0.5889, 0.5107, 0.7798, 0.6199, 0.6940,
    0.9429, 0.9033, 0.5787, 0.925, 0.9793, 0.5453, 0.7104, 0.8852, 0.8093);

float random(float x) { return fract(sin(x * 1234.5) * 5432.1); } // Hardly random but good enough...

// Function adapted from Iñigo Quiles https://iquilezles.org/articles/functions/
float nearidentity(float x) { return x * x * (2.0 - x); }

mat2 rotate(float a) {
    float s = sin(a), c = cos(a);

    return mat2(c, -s, s, c);
}



float gen_line(vec2 uv, float width, float radius) {
    float w = width * (uv.y - radius);
    return smoothstep(w + 0.001, w - 0.001, abs(uv.x));
}

float gen_layer(vec2 uv, float width, float radius) {
    float lines = 0.0;

    for (int i = 0; i < 5; i++) {
        uv *= rotate(TWO_PI * 0.2);
        lines += gen_line(uv, width, radius);
    }
    return lines;
}

void main() {
    vec3 tex = texture2D(DiffuseSampler, texCoord).rgb;
    vec2 uv = texCoord - 0.5;

    for (int i = 0; i < 18; i++) {
        uv *= rotate(TWO_PI * 0.0111 + f_rand[i]);

        float s_time = mod(f_rand[i] * STime * 4.5, 1.8);

        float layer = gen_layer(
            uv,
            f_rand[i] * 0.06,
            f_rand[i] * (nearidentity(s_time) * 0.75 - 0.2) + 0.55);
        layer *= s_time * 6.0;

        tex = mix(tex, vec3(1.0), layer * 0.2);
    }
    gl_FragColor = vec4(tex, 1.0);
}