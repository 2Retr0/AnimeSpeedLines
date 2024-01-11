#version 330

#define PI 3.14159265358979323846
#define SUBDIVISIONS 180

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
    vec3 tex = texture2D(DiffuseSampler, texCoord).rgb;
    vec2 st = texCoord - 0.5f;

    // FIXME: These should be calculated on the CPU
    float strength = Weight; // mod(iTime * 0.1f, 0.8f) + 0.2f;
    float strength_norm = (1.f - 0.8f) * strength + 0.8f;
    // float bias_angle = floor(STime) * 0.25f * (2.f * PI);
    float bias = BiasWeight;
    st += -0.08f * bias * vec2(cos(BiasAngle), sin(BiasAngle));

    // Normalize angle to be between 0 and 1.f
    float angle = atan(st.t, st.s);
    float angle_norm = (0.5f * angle) / PI;

    // These should be calculated on the CPU
    float angle_bias = bias * cos(angle - BiasAngle) + (1.f - bias);
    // We generate a seed for each line based on its angle.
    float s_rand = 0.5f * hash(floor(angle_norm * SUBDIVISIONS)) + 0.5f;

    // We also denote some parameters for the animation progress
    float s_time = s_rand * STime * 7.f;
    float p_shape = fract(s_time); // Animation progress
    float p_iter  = floor(s_time); // Animation Iteration

    // We want an iteration-specific hash so that spike radius isn't dependent
    // purely on angle.
    float p_rand = 0.5f * hash(p_iter) + 0.5f;
    float line_radius = 1.f - pow(max(0.f, 1.6f * p_shape - 0.6f), 2.f);
    float line_alpha =  1.f - 2.f * p_shape * 0.2f * strength;

    st *= 0.6f;                    // Scale out space for 'sharper' lines.
    st *= rotate(s_rand + p_rand); // Rotate by a random amount

    float w = 162.f * line_radius * SUBDIVISIONS / 90.f;
    w *= 0.4f * p_rand;              // Add random factor to spikiness.
    w *= angle_bias * strength_norm; // Incorporate angular bias
    w = max(2.f, w);                 // w ∈ [2,SUBDIVISIONS]

    float d = lines_sdf(st, 0.7f, SUBDIVISIONS, w);
    vec3 col = (d > 0.f) ? vec3(1.f) : tex;
    // Use smoothstep() for anti-aliasing
    col = mix(col, vec3(1.f), 1.f - smoothstep(0.f, 0.001f, abs(d)));
    // Alpha increases when line begins to recede
    col = mix(col, tex, line_alpha);

    // Draw debug lines (red is path of line).
    // col = fract(0.5f * (atan(st.t, st.s) / PI + 1.f) * n) > 0.95f ? vec3(1.f, 0.25f, 0.3f) : col;
    fragColor = vec4(col, 1.f);
}
