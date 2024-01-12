#version 330 core

#define PI 3.14159265358979323846
#define SUBDIVISIONS 128
#define TARGET_ALPHA 0.18
#define RELATIVE_SPEED 7.0

uniform sampler2D DiffuseSampler; // Main texture
uniform float STime;     // Time in seconds + tick delta
uniform float Weight;    // Intensity
uniform float BiasAngle; // Intensity
uniform float BiasWeight;

in vec2 texCoord;
out vec4 fragColor;

// Source: https://www.shadertoy.com/view/Xt3cDn
uint base_hash(uint p) {
    p = 1103515245U * ((p >> 1U) ^ p);
    uint h32 = 1103515245U * (p ^ (p >> 3U));
    return h32 ^ (h32 >> 16);
}

float hash(in float x) {
    return float(base_hash(floatBitsToUint(x))) * (1.f / float(0xFFFFFFFFU));
}

// signed distance to a n-star polygon with external angle en
// Source: https://iquilezles.org/articles/distfunctions2d/
// p : position, r : radius, n : num sides, m : angle divisor
float lines_sdf(in vec2 p, in float r, in int n, in float m) { // m=[2,n]
    // these 4 lines can be precomputed for a given shape
    float an = PI / float(n);
    float en = PI / m;
    vec2 acs = vec2(cos(an), sin(an));
    vec2 ecs = vec2(cos(en), sin(en)); // ecs=vec2(0,1) and simplify, for regular polygon,

    // reduce to first sector
    float bn = mod(atan(p.x, p.y), 2.f * an) - an;
    p = length(p) * vec2(cos(bn), abs(sin(bn)));

    // line sdf
    p -= r * acs;
    p += ecs * clamp(-dot(p, ecs), 0.f, r * acs.y / ecs.y);
    return length(p) * sign(p.x);
}

mat2 rotate(in float a) {
    return mat2(cos(a), -sin(a), sin(a), cos(a));
}

void main() {
    vec3 tex = texture(DiffuseSampler, texCoord).rgb;
    vec2 st = texCoord - 0.5f;

    // These can be calculated on the CPU
    float weight_normalized = mix(0.8f, 1.f, Weight);
    st += -0.08f*BiasWeight * vec2(cos(BiasAngle), sin(BiasAngle));

    // Normalized angle ∈ [0, 1]
    float angle = atan(st.t, st.s);
    float angle_normalized = (0.5f * angle) / PI;

    // These should be calculated on the CPU
    float angle_bias = BiasWeight*cos(angle - BiasAngle) + (1.f - BiasWeight);
    // We generate a seed for each line based on its angle.
    float s_rand = mix(0.5f, 1.f, hash(floor(angle_normalized * SUBDIVISIONS)));

    // We also denote some parameters for the animation progress
    float s_time = RELATIVE_SPEED * s_rand * STime;
    float shape = fract(s_time);     // Animation progress
    float iteration = floor(s_time); // Animation Iteration

    // We want an iteration-specific hash so that spike radius isn't dependent
    // purely on angle.
    float p_rand = 0.5f * hash(iteration) + 0.5f;
    float luminance = dot(tex, vec3(0.299, 0.587, 0.114));
    float target_alpha = TARGET_ALPHA * mix(0.25f, 1.f, luminance);
    float line_radius = 1.f - pow(max(0.f, 1.6f * shape - 0.6f), 2.f);
    float line_alpha =  1.f - 2.f*shape * target_alpha*Weight;

    st *= 0.6f;                    // Scale out space for 'sharper' lines.
    st *= rotate(s_rand + p_rand); // Rotate by a random amount

    float w = 162.f*line_radius * SUBDIVISIONS / 90.f;
    w *= 0.4f * p_rand;                  // Add random factor to spikiness.
    w *= angle_bias * weight_normalized; // Incorporate angular bias
    w = max(2.f, w);                     // w ∈ [2,SUBDIVISIONS]

    float d = lines_sdf(st, 0.7f, SUBDIVISIONS, w);
    vec3 col = (d > 0.f) ? vec3(1.f) : tex;
    // Use smoothstep() for anti-aliasing
    col = mix(tex, vec3(1.f), 1.f - smoothstep(0.f, 0.001f, abs(d)));
    // Alpha increases when line begins to recede
    col = mix(col, tex, line_alpha);
    fragColor = vec4(col, 1.f);
}
